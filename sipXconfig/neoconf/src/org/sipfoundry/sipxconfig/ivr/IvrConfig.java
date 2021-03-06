/**
 *
 *
 * Copyright (c) 2010 / 2011 eZuce, Inc. All rights reserved.
 * Contributed to SIPfoundry under a Contributor Agreement
 *
 * This software is free software; you can redistribute it and/or modify it under
 * the terms of the Affero General Public License (AGPL) as published by the
 * Free Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 */
package org.sipfoundry.sipxconfig.ivr;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.sipfoundry.sipxconfig.address.Address;
import org.sipfoundry.sipxconfig.admin.AdminContext;
import org.sipfoundry.sipxconfig.cfgmgt.ConfigException;
import org.sipfoundry.sipxconfig.cfgmgt.ConfigManager;
import org.sipfoundry.sipxconfig.cfgmgt.ConfigProvider;
import org.sipfoundry.sipxconfig.cfgmgt.ConfigRequest;
import org.sipfoundry.sipxconfig.cfgmgt.ConfigUtils;
import org.sipfoundry.sipxconfig.cfgmgt.KeyValueConfiguration;
import org.sipfoundry.sipxconfig.commserver.Location;
import org.sipfoundry.sipxconfig.dialplan.DialPlanContext;
import org.sipfoundry.sipxconfig.domain.Domain;
import org.sipfoundry.sipxconfig.feature.FeatureManager;
import org.sipfoundry.sipxconfig.freeswitch.FreeswitchFeature;
import org.sipfoundry.sipxconfig.im.ImManager;
import org.sipfoundry.sipxconfig.imbot.ImBot;
import org.sipfoundry.sipxconfig.mwi.Mwi;
import org.sipfoundry.sipxconfig.restserver.RestServer;
import org.springframework.beans.factory.annotation.Required;

public class IvrConfig implements ConfigProvider {
    private Ivr m_ivr;

    @Override
    public void replicate(ConfigManager manager, ConfigRequest request) throws IOException {
        if (!request.applies(DialPlanContext.FEATURE, Ivr.FEATURE, Mwi.FEATURE, RestServer.FEATURE, ImBot.FEATURE,
                FreeswitchFeature.FEATURE, AdminContext.FEATURE, ImManager.FEATURE)) {
            return;
        }
        Set<Location> locations = request.locations(manager);
        FeatureManager featureManager = manager.getFeatureManager();
        Address mwiApi = manager.getAddressManager().getSingleAddress(Mwi.HTTP_API);
        Address adminApi = manager.getAddressManager().getSingleAddress(AdminContext.HTTP_ADDRESS);
        Address restApi = manager.getAddressManager().getSingleAddress(RestServer.HTTP_API);
        Address imApi = manager.getAddressManager().getSingleAddress(ImManager.XMLRPC_ADDRESS);
        Address imbotApi = manager.getAddressManager().getSingleAddress(ImBot.XML_RPC);
        Address fsEvent = manager.getAddressManager().getSingleAddress(FreeswitchFeature.EVENT_ADDRESS);
        IvrSettings settings = m_ivr.getSettings();
        Domain domain = manager.getDomainManager().getDomain();
        for (Location location : locations) {
            File dir = manager.getLocationDataDirectory(location);
            boolean enabled = featureManager.isFeatureEnabled(Ivr.FEATURE, location);

            ConfigUtils.enableCfengineClass(dir, "sipxivr.cfdat", enabled, "sipxivr");
            if (!enabled) {
                continue;
            }
            File f = new File(dir, "sipxivr.properties.part");
            Writer wtr = new FileWriter(f);
            try {
                write(wtr, settings, domain, location, mwiApi, restApi, adminApi, imApi, imbotApi, fsEvent);
            } finally {
                IOUtils.closeQuietly(wtr);
            }
        }
    }

    void write(Writer wtr, IvrSettings settings, Domain domain, Location location, Address mwiApi, Address restApi,
            Address adminApi, Address imApi, Address imbotApi, Address fsEvent) throws IOException {
        KeyValueConfiguration config = KeyValueConfiguration.equalsSeparated(wtr);
        config.write(settings.getSettings());
        config.write("freeswitch.eventSocketPort", fsEvent.getPort());

        // potential bug: name "operator" could be changed by admin. this should be configurable
        // and linked with vm dialing rule
        config.write("ivr.operatorAddr", "sip:operator@" + domain.getName());

        // required services
        if (mwiApi == null) {
            throw new ConfigException("MWI feature needs to be enabled. No addresses found.");
        }
        config.write("ivr.mwiUrl", mwiApi.toString());
        if (adminApi == null) {
            throw new ConfigException("Admin feature needs to be enabled. No addresses found.");
        }
        config.write("ivr.configUrl", adminApi.toString());

        // optional services
        if (restApi != null) {
            config.write("ivr.3pccSecureUrl", restApi.toString());
            config.write("ivr.callHistoryUrl", restApi.toString() + "/cdr/");
        }
        if (imApi != null) {
            config.write("ivr.openfireHost", imApi.getAddress());
            config.write("ivr.openfireXmlRpcPort", imApi.getPort());
        }
        if (imbotApi != null) {
            config.write("ivr.sendIMUrl", imbotApi.toString());
        }
    }

    @Required
    public void setIvr(Ivr ivr) {
        m_ivr = ivr;
    }
}
