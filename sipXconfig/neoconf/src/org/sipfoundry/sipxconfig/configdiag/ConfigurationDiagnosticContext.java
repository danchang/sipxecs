/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.configdiag;

import java.io.File;
import java.util.List;

public interface ConfigurationDiagnosticContext {
    List<ConfigurationDiagnostic> getConfigurationTests();

    void runTests();

    File getPreflightInstaller();
}
