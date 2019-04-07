package org.iota.ictinder;

import org.iota.ict.Ict;
import org.iota.ict.api.RestApi;
import org.iota.ict.ixi.context.IxiContext;
import org.iota.ict.utils.properties.Properties;
import org.json.JSONObject;

public class Main {

    public static void main(String[] args) throws Exception {
        Ict ict = new Ict(new Properties().toEditable().guiPassword(RestApi.hashPassword("secure_pass")).toFinal());
        ict.getModuleHolder().loadVirtualModule(IcTinder.class, "IcTinder");

        IxiContext context = ict.getModuleHolder().getModule("virtual/IcTinder").getContext();
        JSONObject config = context.getDefaultConfiguration()
                .put("node_address", "example.org:1337")
                .put("ict_gui_password", "secure_pass")
                .put("discord_id", "123456789012345678")
                .put("ictinder_password", "abcd1234DEFG5678");
        context.tryToUpdateConfiguration(config);

        ict.getModuleHolder().startAllModules();
    }
}
