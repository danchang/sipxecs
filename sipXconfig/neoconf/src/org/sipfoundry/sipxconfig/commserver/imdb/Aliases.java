/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.commserver.imdb;

import static org.sipfoundry.commons.mongo.MongoConstants.ALIASES;

import org.sipfoundry.sipxconfig.common.Replicable;

import com.mongodb.DBObject;

public class Aliases extends AbstractDataSetGenerator {
    public static final String FAX_EXTENSION_PREFIX = "~~ff~";

    public Aliases() {
    }

    protected DataSet getType() {
        return DataSet.ALIAS;
    }

    public boolean generate(Replicable entity, DBObject top) {
        top.put(ALIASES, entity.getAliasMappings(getCoreContext().getDomainName()));
        return true;
    }

}
