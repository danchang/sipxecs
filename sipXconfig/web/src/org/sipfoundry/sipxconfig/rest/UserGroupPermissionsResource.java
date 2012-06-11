/*
 *
 *  Copyright (C) 2012 PATLive, D. Chang
 *  Contributed to SIPfoundry under a Contributor Agreement
 *  UserGroupPermissionsResource.java - A Restlet to read User Group data with Permissions from SipXecs
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
import java.util.Collection;
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
import org.sipfoundry.sipxconfig.rest.RestUtilities.ResponseCode;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SettingPermissionRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.UserGroupPermissionRestInfoFull;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ValidationInfo;
import org.sipfoundry.sipxconfig.setting.Group;
import org.sipfoundry.sipxconfig.setting.SettingDao;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class UserGroupPermissionsResource extends UserResource {

    private static final String ELEMENT_NAME_USERGROUPPERMISSIONBUNDLE = "user-group-permission";
    private static final String ELEMENT_NAME_USERGROUPPERMISSION = "group";
    private static final String ELEMENT_NAME_SETTINGPERMISSION = "setting";

    private SettingDao m_settingContext; // saveGroup is not available through corecontext
    private PermissionManager m_permissionManager;
    private Form m_form;

    // use to define all possible sort fields
    private enum SortField {
        NAME, DESCRIPTION, NONE;

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

    // GET - Retrieve all and single User Group with Permissions
    // ---------------------------------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        int idInt;
        UserGroupPermissionRestInfoFull userGroupPermissionRestInfo = null;
        String idString = (String) getRequest().getAttributes().get(RestUtilities.REQUEST_ATTRIBUTE_ID);

        if (idString != null) {
            try {
                idInt = RestUtilities.getIntFromAttribute(idString);
            } catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), ResponseCode.ERROR_BAD_ID,
                        RestUtilities.getResponseMessage(ResponseCode.ERROR_BAD_ID, idString));
            }

            try {
                userGroupPermissionRestInfo = createUserGroupPermissionRestInfo(idInt);
            } catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), ResponseCode.ERROR_READ_FAILED,
                        RestUtilities.getResponseMessage(ResponseCode.ERROR_READ_FAILED, this.getClass()
                                .getSimpleName()), exception.getLocalizedMessage());
            }

            return new UserGroupPermissionRepresentation(variant.getMediaType(), userGroupPermissionRestInfo);
        }

        // if not single, process request for all
        List<Group> userGroups = getCoreContext().getGroups(); // settingsContext.getGroups()
                                                               // requires Resource string value

        List<UserGroupPermissionRestInfoFull> userGroupPermissionsRestInfo =
                new ArrayList<UserGroupPermissionRestInfoFull>();
        MetadataRestInfo metadataRestInfo;

        // sort if specified
        sortUserGroups(userGroups);

        // set requested items and get resulting metadata
        metadataRestInfo = addUserGroups(userGroupPermissionsRestInfo, userGroups);

        // create final restinfo
        UserGroupPermissionsBundleRestInfo userGroupPermissionsBundleRestInfo =
                new UserGroupPermissionsBundleRestInfo(userGroupPermissionsRestInfo, metadataRestInfo);

        return new UserGroupPermissionsRepresentation(variant.getMediaType(),
                userGroupPermissionsBundleRestInfo);
    }

    // PUT - Update Permissions
    // ------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        UserGroupPermissionRepresentation representation = new UserGroupPermissionRepresentation(entity);
        UserGroupPermissionRestInfoFull userGroupPermissionRestInfo = representation.getObject();
        Group userGroup = null;

        // validate input for update or create
        ValidationInfo validationInfo = validate(userGroupPermissionRestInfo);

        if (!validationInfo.getValid()) {
            RestUtilities.setResponseError(getResponse(), validationInfo.getResponseCode(),
                    validationInfo.getMessage());
            return;
        }

        // if have id then update single
        String idString = (String) getRequest().getAttributes().get(RestUtilities.REQUEST_ATTRIBUTE_ID);

        if (idString != null) {
            try {
                int idInt = RestUtilities.getIntFromAttribute(idString);
                userGroup = m_settingContext.getGroup(idInt);
            } catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), ResponseCode.ERROR_BAD_ID,
                        RestUtilities.getResponseMessage(ResponseCode.ERROR_BAD_ID, idString));
                return;
            }

            // copy values over to existing
            try {
                updateUserGroupPermission(userGroup, userGroupPermissionRestInfo);
                m_settingContext.saveGroup(userGroup);
            } catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), ResponseCode.ERROR_UPDATE_FAILED,
                        RestUtilities.getResponseMessage(ResponseCode.ERROR_UPDATE_FAILED, this.getClass()
                                .getSimpleName()), exception.getLocalizedMessage());
                return;
            }

            RestUtilities.setResponse(getResponse(), ResponseCode.SUCCESS_UPDATED, RestUtilities
                    .getResponseMessage(ResponseCode.SUCCESS_UPDATED, this.getClass().getSimpleName()),
                    userGroup.getId());

            return;
        }

        // otherwise error, since no creation of new permissions
        RestUtilities.setResponseError(getResponse(), ResponseCode.ERROR_MISSING_ID, RestUtilities
                .getResponseMessage(ResponseCode.ERROR_MISSING_ID, this.getClass().getSimpleName()));
    }

    // Helper functions
    // ----------------

    // basic interface level validation of data provided through REST interface for creation or
    // update
    // may also contain clean up of input data
    // may create another validation function if different rules needed for update v. create
    private ValidationInfo validate(UserGroupPermissionRestInfoFull restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        return validationInfo;
    }

    private UserGroupPermissionRestInfoFull createUserGroupPermissionRestInfo(int id) {
        Group group = m_settingContext.getGroup(id);

        return createUserGroupPermissionRestInfo(group);
    }

    private UserGroupPermissionRestInfoFull createUserGroupPermissionRestInfo(Group group) {
        UserGroupPermissionRestInfoFull userGroupPermissionRestInfo = null;
        List<SettingPermissionRestInfo> settings;

        settings = createSettingsRestInfo(group);
        userGroupPermissionRestInfo = new UserGroupPermissionRestInfoFull(group, settings);

        return userGroupPermissionRestInfo;
    }

    private List<SettingPermissionRestInfo> createSettingsRestInfo(Group group) {
        List<SettingPermissionRestInfo> settings = new ArrayList<SettingPermissionRestInfo>();
        SettingPermissionRestInfo settingRestInfo = null;
        Collection<Permission> permissions;
        String permissionName;
        String permissionLabel;
        String permissionValue;
        boolean defaultValue;

        permissions = m_permissionManager.getPermissions();

        // settings value for permissions are ENABLE or DISABLE instead of boolean
        for (Permission permission : permissions) {
            permissionName = permission.getName();
            permissionLabel = permission.getLabel();

            try {
                // empty return means setting is at default (unless error in input to
                // getSettingValue)
                permissionValue = group.getSettingValue(permission.getSettingPath());
            } catch (Exception exception) {
                permissionValue = "GetSettingValue error: " + exception.getLocalizedMessage();
            }

            defaultValue = permission.getDefaultValue();

            settingRestInfo =
                    new SettingPermissionRestInfo(permissionName, permissionLabel, permissionValue,
                            defaultValue);
            settings.add(settingRestInfo);
        }

        return settings;
    }

    private MetadataRestInfo addUserGroups(
            List<UserGroupPermissionRestInfoFull> userGroupPermissionsRestInfo, List<Group> userGroups) {
        UserGroupPermissionRestInfoFull userGroupPermissionRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = RestUtilities.calculatePagination(m_form, userGroups.size());

        // create list of restinfos
        for (int index = paginationInfo.getStartIndex(); index <= paginationInfo.getEndIndex(); index++) {
            Group userGroup = userGroups.get(index);

            userGroupPermissionRestInfo = createUserGroupPermissionRestInfo(userGroup);
            userGroupPermissionsRestInfo.add(userGroupPermissionRestInfo);
        }

        // create metadata about restinfos
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortUserGroups(List<Group> userGroups) {
        // sort if requested
        SortInfo sortInfo = RestUtilities.calculateSorting(m_form);

        if (!sortInfo.getSort()) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.getSortField());

        if (sortInfo.getDirectionForward()) {

            switch (sortField) {
            case NAME:
                Collections.sort(userGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Group group1 = (Group) object1;
                        Group group2 = (Group) object2;
                        return group1.getName().compareToIgnoreCase(group2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(userGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Group group1 = (Group) object1;
                        Group group2 = (Group) object2;
                        return group1.getDescription().compareToIgnoreCase(group2.getDescription());
                    }

                });
                break;

            default:
                break;
            }
        } else {
            // must be reverse
            switch (sortField) {
            case NAME:
                Collections.sort(userGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Group group1 = (Group) object1;
                        Group group2 = (Group) object2;
                        return group2.getName().compareToIgnoreCase(group1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(userGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Group group1 = (Group) object1;
                        Group group2 = (Group) object2;
                        return group2.getDescription().compareToIgnoreCase(group1.getDescription());
                    }

                });
                break;

            default:
                break;
            }
        }
    }

    private void updateUserGroupPermission(Group userGroup,
            UserGroupPermissionRestInfoFull userGroupPermissionRestInfo) {
        Permission permission;

        // update each permission setting
        for (SettingPermissionRestInfo settingRestInfo : userGroupPermissionRestInfo.getPermissions()) {
            permission = m_permissionManager.getPermissionByName(settingRestInfo.getName());
            userGroup.setSettingValue(permission.getSettingPath(), settingRestInfo.getValue());
        }
    }

    // REST Representations
    // --------------------

    static class UserGroupPermissionsRepresentation extends
            XStreamRepresentation<UserGroupPermissionsBundleRestInfo> {

        public UserGroupPermissionsRepresentation(MediaType mediaType,
                UserGroupPermissionsBundleRestInfo object) {
            super(mediaType, object);
        }

        public UserGroupPermissionsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias(ELEMENT_NAME_USERGROUPPERMISSIONBUNDLE, UserGroupPermissionsBundleRestInfo.class);
            xstream.alias(ELEMENT_NAME_USERGROUPPERMISSION, UserGroupPermissionRestInfoFull.class);
            xstream.alias(ELEMENT_NAME_SETTINGPERMISSION, SettingPermissionRestInfo.class);
        }
    }

    static class UserGroupPermissionRepresentation extends
            XStreamRepresentation<UserGroupPermissionRestInfoFull> {

        public UserGroupPermissionRepresentation(MediaType mediaType, UserGroupPermissionRestInfoFull object) {
            super(mediaType, object);
        }

        public UserGroupPermissionRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias(ELEMENT_NAME_USERGROUPPERMISSION, UserGroupPermissionRestInfoFull.class);
            xstream.alias(ELEMENT_NAME_SETTINGPERMISSION, SettingPermissionRestInfo.class);
        }
    }

    // REST info objects
    // -----------------

    static class UserGroupPermissionsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<UserGroupPermissionRestInfoFull> m_groups;

        public UserGroupPermissionsBundleRestInfo(List<UserGroupPermissionRestInfoFull> userGroupPermissions,
                MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_groups = userGroupPermissions;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<UserGroupPermissionRestInfoFull> getGroups() {
            return m_groups;
        }
    }

    // Injected objects
    // ----------------

    @Required
    public void setSettingDao(SettingDao settingContext) {
        m_settingContext = settingContext;
    }

    @Required
    public void setPermissionManager(PermissionManager permissionManager) {
        m_permissionManager = permissionManager;
    }

}
