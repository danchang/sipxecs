/*
 *
 *  Copyright (C) 2012 PATLive, D. Chang
 *  Contributed to SIPfoundry under a Contributor Agreement
 *  PermissionsResource.java - A Restlet to read Skill data from SipXecs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.

 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.sipfoundry.sipxconfig.rest;

import static org.restlet.data.MediaType.APPLICATION_JSON;
import static org.restlet.data.MediaType.TEXT_XML;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sipfoundry.sipxconfig.permission.Permission;
import org.sipfoundry.sipxconfig.permission.PermissionManager;
import org.sipfoundry.sipxconfig.rest.RestUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.PermissionRestInfoFull;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ResponseCode;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ValidationInfo;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class PermissionsResource extends UserResource {

    private static final String ELEMENT_NAME_PERMISSIONBUNDLE = "permissions";
    private static final String ELEMENT_NAME_PERMISSION = "permission";

    private PermissionManager m_permissionManager;
    private Form m_form;

    // use to define all possible sort fields
    private enum SortField {
        NAME, DESCRIPTION, LABEL, DEFAULTVALUE, NONE;

        public static SortField toSortField(String fieldString) {
            if (fieldString == null) {
                return NONE;
            }

            try {
                return valueOf(fieldString.toUpperCase());
            } catch (Exception ex) {
                return NONE;
            }
        }
    }

    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        getVariants().add(new Variant(TEXT_XML));
        getVariants().add(new Variant(APPLICATION_JSON));

        // pull parameters from url
        m_form = getRequest().getResourceRef().getQueryAsForm();
    }

    // Allowed REST operations
    // -----------------------

    @Override
    public boolean allowGet() {
        return true;
    }

    @Override
    public boolean allowPut() {
        return true;
    }

    @Override
    public boolean allowDelete() {
        return true;
    }

    // GET - Retrieve all and single Skill
    // -----------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        // Permissions do not use Id, so must key off Name
        PermissionRestInfoFull permissionRestInfo = null;
        String nameString = (String) getRequest().getAttributes().get(RestUtilities.REQUEST_ATTRIBUTE_NAME);

        if (nameString != null) {
            try {
                permissionRestInfo = createPermissionRestInfo(nameString);
            } catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), ResponseCode.ERROR_READ_FAILED,
                        RestUtilities.getResponseMessage(ResponseCode.ERROR_READ_FAILED, this.getClass()
                                .getSimpleName()), exception.getLocalizedMessage());
            }

            return new PermissionRepresentation(variant.getMediaType(), permissionRestInfo);
        }

        // if not single, process request for all
        List<Permission> permissions = new ArrayList<Permission>(m_permissionManager.getPermissions());
        List<PermissionRestInfoFull> permissionsRestInfo = new ArrayList<PermissionRestInfoFull>();
        MetadataRestInfo metadataRestInfo;

        // sort groups if specified
        sortPermissions(permissions);

        // set requested agents groups and get resulting metadata
        metadataRestInfo = addPermissions(permissionsRestInfo, permissions);

        // create final restinfo
        PermissionsBundleRestInfo permissionsBundleRestInfo =
                new PermissionsBundleRestInfo(permissionsRestInfo, metadataRestInfo);

        return new PermissionsRepresentation(variant.getMediaType(), permissionsBundleRestInfo);
    }

    // PUT - Update or Add single Skill
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        PermissionRepresentation representation = new PermissionRepresentation(entity);
        PermissionRestInfoFull permissionRestInfo = representation.getObject();
        Permission permission = null;

        // validate input for update or create
        ValidationInfo validationInfo = validate(permissionRestInfo);

        if (!validationInfo.getValid()) {
            RestUtilities.setResponseError(getResponse(), validationInfo.getResponseCode(),
                    validationInfo.getMessage());
            return;
        }

        // if have id then update single
        String nameString = (String) getRequest().getAttributes().get(RestUtilities.REQUEST_ATTRIBUTE_NAME);

        if (nameString != null) {
            try {
                permission = m_permissionManager.getPermissionByName(nameString);
            } catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), ResponseCode.ERROR_BAD_ID,
                        RestUtilities.getResponseMessage(ResponseCode.ERROR_BAD_ID, nameString));
                return;
            }

            // copy values over to existing
            try {
                updatePermission(permission, permissionRestInfo);
                m_permissionManager.saveCallPermission(permission);
            } catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), ResponseCode.ERROR_UPDATE_FAILED,
                        RestUtilities.getResponseMessage(ResponseCode.ERROR_UPDATE_FAILED, this.getClass()
                                .getSimpleName()), exception.getLocalizedMessage());
                return;
            }

            RestUtilities.setResponse(getResponse(), ResponseCode.SUCCESS_UPDATED, RestUtilities
                    .getResponseMessage(ResponseCode.SUCCESS_UPDATED, this.getClass().getSimpleName()),
                    permission.getName());

            return;
        }

        // otherwise add new
        try {
            permission = createPermission(permissionRestInfo);
            m_permissionManager.saveCallPermission(permission);
        } catch (Exception exception) {
            RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_CREATE_FAILED,
                    RestUtilities.getResponseMessage(ResponseCode.ERROR_CREATE_FAILED, this.getClass()
                            .getSimpleName()), exception.getLocalizedMessage());
            return;
        }

        RestUtilities.setResponse(getResponse(), ResponseCode.SUCCESS_CREATED, RestUtilities
                .getResponseMessage(ResponseCode.SUCCESS_CREATED, this.getClass().getSimpleName()),
                permission.getName());
    }

    // DELETE - Delete single Skill
    // ----------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        Permission permission;

        // get id then delete single
        String nameString = (String) getRequest().getAttributes().get(RestUtilities.REQUEST_ATTRIBUTE_NAME);

        if (nameString != null) {
            try {
                permission = m_permissionManager.getPermissionByName(nameString);
            } catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_ID,
                        RestUtilities.getResponseMessage(ResponseCode.ERROR_BAD_ID, nameString));
                return;
            }

            m_permissionManager.deleteCallPermission(permission);

            RestUtilities.setResponse(getResponse(), ResponseCode.SUCCESS_DELETED, RestUtilities
                    .getResponseMessage(ResponseCode.SUCCESS_DELETED, this.getClass().getSimpleName()),
                    permission.getName());

            return;
        }

        // no id string
        RestUtilities.setResponse(getResponse(), ResponseCode.ERROR_MISSING_ID, RestUtilities
                .getResponseMessage(ResponseCode.ERROR_MISSING_ID, this.getClass().getSimpleName()));
    }

    // Helper functions
    // ----------------

    // basic interface level validation of data provided through REST interface for creation or
    // update
    // may also contain clean up of input data
    // may create another validation function if different rules needed for update v. create
    private ValidationInfo validate(PermissionRestInfoFull restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        return validationInfo;
    }

    private PermissionRestInfoFull createPermissionRestInfo(String name) throws ResourceException {
        PermissionRestInfoFull permissionRestInfo = null;

        Permission permission = m_permissionManager.getPermissionByName(name);
        permissionRestInfo = new PermissionRestInfoFull(permission);

        return permissionRestInfo;
    }

    private MetadataRestInfo addPermissions(List<PermissionRestInfoFull> permissionsRestInfo,
            List<Permission> permissions) {
        PermissionRestInfoFull permissionRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = RestUtilities.calculatePagination(m_form, permissions.size());

        // create list of restinfos
        for (int index = paginationInfo.getStartIndex(); index <= paginationInfo.getEndIndex(); index++) {
            Permission permission = permissions.get(index);

            permissionRestInfo = new PermissionRestInfoFull(permission);
            permissionsRestInfo.add(permissionRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortPermissions(List<Permission> permissions) {
        // sort if requested
        SortInfo sortInfo = RestUtilities.calculateSorting(m_form);

        if (!sortInfo.getSort()) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.getSortField());

        if (sortInfo.getDirectionForward()) {

            switch (sortField) {
            case LABEL:
                Collections.sort(permissions, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Permission permission1 = (Permission) object1;
                        Permission permission2 = (Permission) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(permission1.getLabel(),
                                permission2.getLabel());
                    }

                });
                break;

            case DEFAULTVALUE:
                Collections.sort(permissions, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Permission permission1 = (Permission) object1;
                        Permission permission2 = (Permission) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(
                                Boolean.toString(permission1.getDefaultValue()),
                                Boolean.toString(permission2.getDefaultValue()));
                    }

                });
                break;

            case NAME:
                Collections.sort(permissions, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Permission permission1 = (Permission) object1;
                        Permission permission2 = (Permission) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(permission1.getName(),
                                permission2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(permissions, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Permission permission1 = (Permission) object1;
                        Permission permission2 = (Permission) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(permission1.getDescription(),
                                permission2.getDescription());
                    }

                });
                break;

            default:
                break;
            }
        } else {
            // must be reverse
            switch (sortField) {
            case LABEL:
                Collections.sort(permissions, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Permission permission1 = (Permission) object1;
                        Permission permission2 = (Permission) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(permission2.getLabel(),
                                permission1.getLabel());
                    }

                });
                break;

            case DEFAULTVALUE:
                Collections.sort(permissions, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Permission permission1 = (Permission) object1;
                        Permission permission2 = (Permission) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(
                                Boolean.toString(permission2.getDefaultValue()),
                                Boolean.toString(permission1.getDefaultValue()));
                    }

                });
                break;

            case NAME:
                Collections.sort(permissions, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Permission permission1 = (Permission) object1;
                        Permission permission2 = (Permission) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(permission2.getName(),
                                permission1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(permissions, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Permission permission1 = (Permission) object1;
                        Permission permission2 = (Permission) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(permission2.getDescription(),
                                permission1.getDescription());
                    }

                });
                break;

            default:
                break;
            }
        }
    }

    private void updatePermission(Permission permission, PermissionRestInfoFull permissionRestInfo) {
        String tempString;

        // do not allow empty label
        tempString = permissionRestInfo.getLabel();
        if (!tempString.isEmpty()) {
            permission.setLabel(tempString);
        }

        permission.setDescription(permissionRestInfo.getDescription());
        permission.setDefaultValue(permissionRestInfo.getDefaultValue());
    }

    private Permission createPermission(PermissionRestInfoFull permissionRestInfo) throws ResourceException {
        Permission permission = new Permission();

        // copy fields from rest info
        permission.setLabel(permissionRestInfo.getLabel());
        permission.setDescription(permissionRestInfo.getDescription());
        permission.setDefaultValue(permissionRestInfo.getDefaultValue());

        // only available is custom call types
        permission.setType(Permission.Type.CALL);

        return permission;
    }

    // REST Representations
    // --------------------

    static class PermissionsRepresentation extends XStreamRepresentation<PermissionsBundleRestInfo> {

        public PermissionsRepresentation(MediaType mediaType, PermissionsBundleRestInfo object) {
            super(mediaType, object);
        }

        public PermissionsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias(ELEMENT_NAME_PERMISSIONBUNDLE, PermissionsBundleRestInfo.class);
            xstream.alias(ELEMENT_NAME_PERMISSION, PermissionRestInfoFull.class);
        }
    }

    static class PermissionRepresentation extends XStreamRepresentation<PermissionRestInfoFull> {

        public PermissionRepresentation(MediaType mediaType, PermissionRestInfoFull object) {
            super(mediaType, object);
        }

        public PermissionRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias(ELEMENT_NAME_PERMISSION, PermissionRestInfoFull.class);
        }
    }

    // REST info objects
    // -----------------

    static class PermissionsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<PermissionRestInfoFull> m_permissions;

        public PermissionsBundleRestInfo(List<PermissionRestInfoFull> permissions, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_permissions = permissions;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<PermissionRestInfoFull> getPermissions() {
            return m_permissions;
        }
    }

    // Injected objects
    // ----------------

    @Required
    public void setPermissionManager(PermissionManager permissionManager) {
        m_permissionManager = permissionManager;
    }
}
