/**
 *
 *
 * Copyright (c) 2011 eZuce, Inc. All rights reserved.
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
package org.sipfoundry.sipxconfig.openfire;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.sipfoundry.sipxconfig.address.Address;
import org.sipfoundry.sipxconfig.address.AddressManager;
import org.sipfoundry.sipxconfig.address.AddressProvider;
import org.sipfoundry.sipxconfig.address.AddressType;
import org.sipfoundry.sipxconfig.commserver.Location;
import org.sipfoundry.sipxconfig.feature.Bundle;
import org.sipfoundry.sipxconfig.feature.FeatureChangeRequest;
import org.sipfoundry.sipxconfig.feature.FeatureChangeValidator;
import org.sipfoundry.sipxconfig.feature.FeatureManager;
import org.sipfoundry.sipxconfig.feature.FeatureProvider;
import org.sipfoundry.sipxconfig.feature.GlobalFeature;
import org.sipfoundry.sipxconfig.feature.LocationFeature;
import org.sipfoundry.sipxconfig.firewall.DefaultFirewallRule;
import org.sipfoundry.sipxconfig.firewall.FirewallManager;
import org.sipfoundry.sipxconfig.firewall.FirewallProvider;
import org.sipfoundry.sipxconfig.firewall.FirewallRule;
import org.sipfoundry.sipxconfig.im.ImManager;
import org.sipfoundry.sipxconfig.setting.BeanWithSettingsDao;
import org.sipfoundry.sipxconfig.snmp.ProcessDefinition;
import org.sipfoundry.sipxconfig.snmp.ProcessProvider;
import org.sipfoundry.sipxconfig.snmp.SnmpManager;
import org.springframework.beans.factory.annotation.Required;

public class OpenfireImpl extends ImManager implements FeatureProvider, AddressProvider, ProcessProvider, Openfire,
    FirewallProvider {
    private static final Collection<AddressType> ADDRESSES = Arrays.asList(new AddressType[] {
        XMPP_ADDRESS, XMLRPC_ADDRESS, XMLRPC_VCARD_ADDRESS, WATCHER_ADDRESS
    });
    private BeanWithSettingsDao<OpenfireSettings> m_settingsDao;
    private String m_openfireHome;

    @Override
    public OpenfireSettings getSettings() {
        return m_settingsDao.findOrCreateOne();
    }

    @Override
    public void saveSettings(OpenfireSettings settings) {
        m_settingsDao.upsert(settings);
    }

    @Override
    public Collection<GlobalFeature> getAvailableGlobalFeatures(FeatureManager featureManager) {
        return null;
    }

    @Override
    public Collection<LocationFeature> getAvailableLocationFeatures(FeatureManager featureManager, Location l) {
        return Collections.singleton(FEATURE);
    }

    @Override
    public Collection<Address> getAvailableAddresses(AddressManager manager, AddressType type,
            Location whoIsAsking) {
        if (!ADDRESSES.contains(type)) {
            return null;
        }
        Collection<Location> locations = manager.getFeatureManager().getLocationsForEnabledFeature(FEATURE);
        // only zero or one at this time allowed
        if (locations.isEmpty()) {
            return null;
        }

        OpenfireSettings settings = getSettings();
        List<Address> addresses = new ArrayList<Address>(locations.size());
        for (Location location : locations) {
            Address address = null;
            if (type.equals(XMPP_ADDRESS)) {
                address = new Address(XMPP_ADDRESS, location.getAddress(), settings.getXmppPort());
            } else if (type.equals(XMLRPC_ADDRESS)) {
                address = new Address(XMLRPC_ADDRESS, location.getAddress(), settings.getXmlRpcPort());
            } else if (type.equals(XMLRPC_VCARD_ADDRESS)) {
                address = new Address(XMLRPC_VCARD_ADDRESS, location.getAddress(), settings.getXmlRpcVcardPort());
            } else if (type.equals(WATCHER_ADDRESS)) {
                address = new Address(WATCHER_ADDRESS, location.getAddress(), settings.getWatcherPort());
            }
            addresses.add(address);
        }

        return addresses;
    }

    public void setSettingsDao(BeanWithSettingsDao<OpenfireSettings> settingsDao) {
        m_settingsDao = settingsDao;
    }

    @Override
    public void getBundleFeatures(FeatureManager featureManager, Bundle b) {
        if (b == Bundle.IM) {
            b.addFeature(FEATURE);
        }
    }

    @Override
    public Collection<ProcessDefinition> getProcessDefinitions(SnmpManager manager, Location location) {
        if (!manager.getFeatureManager().isFeatureEnabled(FEATURE, location)) {
            return null;
        }
        return Collections.singleton(ProcessDefinition.sipxDefault("sipxopenfire",
                ".*\\s-Dexe4j.moduleName=$(sipx.OPENFIRE_HOME)/bin/openfire\\s.*"));
    }

    @Required
    public void setOpenfireHome(String openfireHome) {
        m_openfireHome = openfireHome;
    }

    @Override
    public Collection<DefaultFirewallRule> getFirewallRules(FirewallManager manager) {
        List<DefaultFirewallRule> rules = DefaultFirewallRule.rules(Arrays.asList(XMLRPC_ADDRESS, WATCHER_ADDRESS));
        rules.add(new DefaultFirewallRule(XMPP_ADDRESS, FirewallRule.SystemId.PUBLIC));
        return rules;
    }

    @Override
    public void featureChangePrecommit(FeatureManager manager, FeatureChangeValidator validator) {        
        // require postgres ? that's about all i can think of but we do not have a role for that -- Douglas
        validator.singleLocationOnly(FEATURE);
    }

    @Override
    public void featureChangePostcommit(FeatureManager manager, FeatureChangeRequest request) {
    }
}
