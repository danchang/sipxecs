/**
 *
 *
 * Copyright (c) 2012 eZuce, Inc. All rights reserved.
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
package org.sipfoundry.sipxconfig.web.plugin;

import org.apache.tapestry.annotations.Bean;
import org.apache.tapestry.annotations.InjectObject;
import org.apache.tapestry.event.PageBeginRenderListener;
import org.apache.tapestry.event.PageEvent;
import org.sipfoundry.sipxconfig.components.PageWithCallback;
import org.sipfoundry.sipxconfig.components.SipxValidationDelegate;
import org.sipfoundry.sipxconfig.openfire.Openfire;
import org.sipfoundry.sipxconfig.openfire.OpenfireSettings;

public abstract class InstantMessagingPage extends PageWithCallback implements PageBeginRenderListener {
    public static final String PAGE = "plugin/InstantMessagingPage";

    @Bean
    public abstract SipxValidationDelegate getValidator();

    @InjectObject("spring:openfire")
    public abstract Openfire getOpenfire();

    public abstract OpenfireSettings getSettings();

    public abstract void setSettings(OpenfireSettings settings);

    @Override
    public void pageBeginRender(PageEvent arg0) {
        if (getSettings() == null) {
            setSettings(getOpenfire().getSettings());
        }
    }

    public void apply() {
        getOpenfire().saveSettings(getSettings());
    }
}
