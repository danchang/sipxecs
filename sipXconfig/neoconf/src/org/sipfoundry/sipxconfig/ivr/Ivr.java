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

import org.sipfoundry.sipxconfig.address.AddressType;
import org.sipfoundry.sipxconfig.feature.GlobalFeature;
import org.sipfoundry.sipxconfig.feature.LocationFeature;

public interface Ivr {
    public static final LocationFeature FEATURE = new LocationFeature("ivr");
    public static final GlobalFeature CALLPILOT = new GlobalFeature("callpilot");
    public static final AddressType SIP_ADDRESS = AddressType.sipTcp("ivr-sip");
    public static final AddressType REST_API = new AddressType("ivrRestApi", "http://%s:%d");

    public IvrSettings getSettings();

    public CallPilotSettings getCallPilotSettings();

    public void saveSettings(IvrSettings settings);

    public void saveCallPilotSettings(CallPilotSettings settings);
}
