package org.iota.ictinder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.iota.ict.Ict;
import org.iota.ict.api.HttpGateway;
import org.iota.ict.ixi.Ixi;
import org.iota.ict.ixi.IxiModule;
import org.iota.ict.ixi.context.ConfigurableIxiContext;
import org.iota.ict.ixi.context.IxiContext;
import org.iota.ict.utils.properties.EditableProperties;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class IcTinder extends IxiModule {

    private static final String ICTINDER_API = "https://qubiota.com/ictinder/api.php";

    private int guiPort;
    private String nodeAddress;
    private String guiPassword;
    private String discordID;
    private String icTinderPassword;
    private Set<String> staticNeighbors = new HashSet<>();
    private final IxiContext context = new IcTinderContext();
    private static final long SYNC_INTERVAL_MS = 3*60000;
    private long last_sync = 0;
    private double timeout_factor = 1;
    private static final Logger LOGGER = LogManager.getLogger("IcTinder");

    private static final String[] statKeys = {"all", "new", "requested", "invalid", "ignored"};

    public IcTinder(Ixi ixi) {
        super(ixi);
    }

    @Override
    public void run() {
        while (isRunning()) {
            sync();
            try {
                Thread.sleep((long)Math.max(1, SYNC_INTERVAL_MS * timeout_factor + last_sync - System.currentTimeMillis()));
            } catch (InterruptedException e) { }
        }
    }

    @Override
    public void onTerminate() {
        runningThread.interrupt();
    }

    public static void main(String[] args) throws InterruptedException {
        Ict ict = new Ict(new EditableProperties().toFinal());
        (new IcTinder(ict)).start();
        Thread.sleep(100000);
    }

    private void sync() {
        last_sync = System.currentTimeMillis();
        if(nodeAddress.startsWith("localhost")) {
            LOGGER.warn("Cannot sync with IcTinder API: please configure IcTinder.ixi first.");
            return;
        }
        LOGGER.debug("syncing ...");
        try {
            JSONArray neighborsJSON = getNeighbors();
            List<String> currentNeighbors = extractNeighborAddresses(neighborsJSON);
            Map<String, String> params = buildRequestParameters(neighborsJSON);
            String response = HttpGateway.sendPostRequest(ICTINDER_API, params, new HashMap<String, String>());
            processResponse(currentNeighbors, response);
            timeout_factor = 1;
        } catch (Throwable e) {
            LOGGER.error("Unexpected issue occurred during syncing.", e);
            e.printStackTrace();
            timeout_factor = Math.min(timeout_factor * 1.4, 8);
        }
    }

    private Map<String, String> buildRequestParameters(JSONArray neighborsJSON) {
        Map<String, String> requestParameters = new HashMap<>();
        JSONObject statsOfAllNeighbor = collectStatsOfAllNeighbors(neighborsJSON);
        requestParameters.put("username", discordID);
        requestParameters.put("node", nodeAddress);
        requestParameters.put("password", icTinderPassword);
        requestParameters.put("static", staticNeighbors.size()+"");
        requestParameters.put("stats", statsOfAllNeighbor.toString());
        return requestParameters;
    }

    private JSONObject collectStatsOfAllNeighbors(JSONArray neighborsJSON) {
        JSONObject allStats = new JSONObject();
        for(int i = 0; i < neighborsJSON.length(); i++) {
            JSONObject neighbor = neighborsJSON.getJSONObject(i);
            String neighborAddress = neighbor.getString("address");
            if(staticNeighbors.contains(neighborAddress))
                continue;
            JSONArray statsOfRounds = neighbor.getJSONArray("stats");
            JSONObject stats = sumUpAllStats(statsOfRounds);
            allStats.put(neighborAddress, stats);
        }
        return allStats;
    }

    private static JSONObject sumUpAllStats(JSONArray statsOfRounds) {
        JSONObject stats = new JSONObject();
        for(String statKey : statKeys)
            stats.put(statKey, 0);
        for(int j = 0; j < statsOfRounds.length(); j++) {
            JSONObject statsFromSomeRound = statsOfRounds.getJSONObject(j);
            if(statsFromSomeRound.getLong("timestamp")/1000 > (System.currentTimeMillis()-SYNC_INTERVAL_MS)/1000) // divide both sides by 1000 for clarification that both are ms
                accumulateStats(stats, statsFromSomeRound);
        }
        return stats;
    }

    private static void accumulateStats(JSONObject statsAccumulator, JSONObject statsToAdd) {
        for(String statKey : statKeys) {
            int oldValue = statsAccumulator.getInt(statKey);
            int toAdd = statsToAdd.getInt(statKey);
            statsAccumulator.put(statKey, oldValue + toAdd);
        }
    }

    private void processResponse(List<String> currentNeighbors, String responseString) {
        LOGGER.info("IcTinder API Response:    " + responseString);
        JSONObject responseJSON = new JSONObject(responseString);
        if(!responseJSON.getBoolean("success"))
            throw new RuntimeException("IcTinder API Error: " + responseJSON.getString("error"));
        List<String> neighbors = extractNeighborFromResponse(responseJSON);
        updateIctNeighbor(currentNeighbors, neighbors);
        //ict.updateProperties(ict.getProperties().toEditable().neighbors(newNeighbors).toFinal());
    }

    private List<String> extractNeighborFromResponse(JSONObject response) {
        JSONArray neighborsJSON = response.getJSONArray("neighbors");
        List<String> neighbors = new LinkedList<>();
        for(Object newNeighbor : neighborsJSON)
            neighbors.add(newNeighbor.toString());
        return neighbors;
    }

    private void updateIctNeighbor(List<String> currentNeighbors, List<String> newNeighbors) {

        for(String currentNeighbor : currentNeighbors)
            if(!newNeighbors.contains(currentNeighbor) && !staticNeighbors.contains(currentNeighbor))
                removeNeighbor(currentNeighbor);

        for(String newNeighbor : newNeighbors)
            if(!currentNeighbors.contains(newNeighbor))
                addNeighbor(newNeighbor);
    }

    private static List<String> extractNeighborAddresses(JSONArray neighborsJSON) {
        List<String> neighbors = new LinkedList<>();
        for(int i = 0; i < neighborsJSON.length(); i++)
            neighbors.add(neighborsJSON.getJSONObject(i).getString("address"));
        return neighbors;
    }

    private JSONArray getNeighbors() {
        Map<String, String> params = new HashMap<>();
        params.put("timestamp_min", "" + (System.currentTimeMillis() - SYNC_INTERVAL_MS));
        JSONObject getNeighborsResponse = sendIctApiRequest(IctApiPath.GET_NEIGHBORS, params);
        return getNeighborsResponse.getJSONArray("neighbors");

    }

    private void addNeighbor(String address) {
        Map<String, String> params = new HashMap<>();
        params.put("address", address);
        sendIctApiRequest(IctApiPath.ADD_NEIGHBOR, params);
    }

    private void removeNeighbor(String address) {
        Map<String, String> params = new HashMap<>();
        params.put("address", address);
        sendIctApiRequest(IctApiPath.REMOVE_NEIGHBOR, params);
    }

    private JSONObject sendIctApiRequest(IctApiPath path, Map<String, String> params) {
        params.put("password", guiPassword);
        String responseString = HttpGateway.sendPostRequest(ictApi() + path, params, new HashMap<String, String>());
        JSONObject responseJSON = new JSONObject(responseString);
        if(!responseJSON.getBoolean("success"))
            throw new RuntimeException("error returned by Ict api: " + responseJSON.getString("error"));
        return responseJSON;
    }

    private String ictApi() {
        return "http://localhost:"+guiPort+"/";
    }

    private static class IctApiPath {

        private static final IctApiPath GET_NEIGHBORS = new IctApiPath("getNeighbors");
        private static final IctApiPath ADD_NEIGHBOR = new IctApiPath("addNeighbor");
        private static final IctApiPath REMOVE_NEIGHBOR = new IctApiPath("removeNeighbor");

        private final String path;

        IctApiPath(String path) {
            this.path = path;
        }

        @Override
        public String toString() {
            return path;
        }
    }

    private enum Field {
        ict_gui_port, ict_gui_password, ictinder_password, discord_id, node_address, static_neighbors;
    }

    private class IcTinderContext extends ConfigurableIxiContext {

        private IcTinderContext() {
            super(new JSONObject()
                    .put(Field.ict_gui_port.name(), 2187)
                    .put(Field.ict_gui_password.name(), "change_me_now")
                    .put(Field.ictinder_password.name(), "[ICTINDER BOT]")
                    .put(Field.discord_id.name(), "[ICTINDER BOT]")
                    .put(Field.node_address.name(), "localhost:1337")
                    .put(Field.static_neighbors.name(), ""));
            applyConfiguration();
        }

        @Override
        protected void validateConfiguration(JSONObject newConfiguration) {
            for(Field field : Field.values())
                if(!newConfiguration.has(field.name()))
                    throw new IllegalPropertyException(field, "does not exist");
            validateIctGUIPort(newConfiguration);
            validateIctGUIPassword(newConfiguration);
            validateIcTinderPassword(newConfiguration);
            validateDiscordID(newConfiguration);
            validateNodeAddress(newConfiguration);
            validateStaticNeighbors(newConfiguration);
        }

        private void validateIctGUIPort(JSONObject newConfiguration) {
            int guiPort = Integer.parseInt(newConfiguration.get(Field.ict_gui_port.name()).toString());
            if(guiPort < 0 || guiPort > 65535)
                throw new IllegalPropertyException(Field.ict_gui_port, "not in interval [0,65535]");
        }

        private void validateIctGUIPassword(JSONObject newConfiguration) {
            if(!(newConfiguration.get(Field.ict_gui_password.name()) instanceof String))
                throw new IllegalPropertyException(Field.ict_gui_password, "not a String");
            String guiPassword = newConfiguration.getString(Field.ict_gui_password.name());
            if(guiPassword.length() < 8)
                throw new IllegalPropertyException(Field.ict_gui_password, "too short, not secure (change ict config)");
            if(guiPassword.equals("change_me_now"))
                throw new IllegalPropertyException(Field.ict_gui_password, "why haven't you changed it yet? Seriously, the password asked you to do one simple thing. 'change_me_now' is NOT a secure password.");
        }

        private void validateIcTinderPassword(JSONObject newConfiguration) {
            if(!(newConfiguration.get(Field.ictinder_password.name()) instanceof String))
                throw new IllegalPropertyException(Field.ictinder_password, "not a String");
        }

        private void validateDiscordID(JSONObject newConfiguration) {
            if(!(newConfiguration.get(Field.discord_id.name()) instanceof String))
                throw new IllegalPropertyException(Field.discord_id, "not a String");
            String discordID = newConfiguration.getString(Field.discord_id.name());
            if(!discordID.matches("^[0-9]{14,22}$"))
                throw new IllegalPropertyException(Field.discord_id, "not a discord ID (should only contain digits)");
        }

        private void validateNodeAddress(JSONObject newConfiguration) {
            if(!(newConfiguration.get(Field.node_address.name()) instanceof String))
                throw new IllegalPropertyException(Field.node_address, "not a String");
            String nodeAddress = newConfiguration.getString(Field.node_address.name());
            if(!nodeAddress.matches("[a-zA-Z.\\-_0-9]+:\\d{1,5}"))
                throw new IllegalPropertyException(Field.node_address, "not matching expected format 'HOST:PORT'");
            if(nodeAddress.startsWith("localhost:") || nodeAddress.startsWith("0.0.0.0:") || nodeAddress.startsWith("192.168."))
                throw new IllegalPropertyException(Field.node_address, "please specify a host that can be addressed by other Ict nodes");
        }

        private void validateStaticNeighbors(JSONObject newConfiguration) {
            if(!(newConfiguration.get(Field.static_neighbors.name()) instanceof String))
                throw new IllegalPropertyException(Field.static_neighbors, "not a String");
        }

        private class IllegalPropertyException extends IllegalArgumentException {
            private IllegalPropertyException(Field field, String cause) {
                super("Invalid property '"+field.name()+"': " + cause + ".");
            }
        }

        @Override
        protected void applyConfiguration() {
            guiPort = configuration.getInt(Field.ict_gui_port.name());
            guiPassword = configuration.getString(Field.ict_gui_password.name());
            icTinderPassword = configuration.getString(Field.ictinder_password.name());
            discordID = configuration.getString(Field.discord_id.name());
            nodeAddress = configuration.getString(Field.node_address.name());
            staticNeighbors = new HashSet<>(Arrays.asList(configuration.getString(Field.static_neighbors.name()).replace(" ", "").split(",")));
            staticNeighbors.remove("");
        }
    }

    @Override
    public IxiContext getContext() {
        return context;
    }
}
