/*
 *
 *  Copyright (C) 2012 PATLive, D. Chang
 *  Contributed to SIPfoundry under a Contributor Agreement
 *  OpenAcdAgentGroupsResource.java - A Restlet to read Skill data from OpenACD within SipXecs
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
import java.util.HashSet;
import java.util.List;

import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkill;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkillGroup;
import org.sipfoundry.sipxconfig.rest.RestUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdSkillGroupRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ResponseCode;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ValidationInfo;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class OpenAcdSkillGroupsResource extends UserResource {

    private static final String ELEMENT_NAME_SKILLGROUPBUNDLE = "openacd-skill-group";
    private static final String ELEMENT_NAME_SKILLGROUP = "group";

    private OpenAcdContext m_openAcdContext;
    private Form m_form;

    // use to define all possible sort fields
    private enum SortField {
        NAME, DESCRIPTION, NUMBERSKILLS, NONE;

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
        int idInt;
        OpenAcdSkillGroupRestInfo skillGroupRestInfo = null;
        String idString = (String) getRequest().getAttributes().get(RestUtilities.REQUEST_ATTRIBUTE_ID);

        if (idString != null) {
            try {
                idInt = RestUtilities.getIntFromAttribute(idString);
            } catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), ResponseCode.ERROR_BAD_ID,
                        RestUtilities.getResponseMessage(ResponseCode.ERROR_BAD_ID, idString));
            }

            try {
                skillGroupRestInfo = createSkillGroupRestInfo(idInt);
            } catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), ResponseCode.ERROR_READ_FAILED,
                        RestUtilities.getResponseMessage(ResponseCode.ERROR_READ_FAILED, this.getClass()
                                .getSimpleName()), exception.getLocalizedMessage());
            }

            return new OpenAcdSkillGroupRepresentation(variant.getMediaType(), skillGroupRestInfo);
        }

        // if not single, process request for list
        List<OpenAcdSkillGroup> skillGroups = m_openAcdContext.getSkillGroups();
        List<OpenAcdSkillGroupRestInfo> skillGroupsRestInfo = new ArrayList<OpenAcdSkillGroupRestInfo>();
        MetadataRestInfo metadataRestInfo;

        // sort groups if specified
        sortSkillGroups(skillGroups);

        // set requested records and get resulting metadata
        metadataRestInfo = addSkillGroups(skillGroupsRestInfo, skillGroups);

        // create final restinfo
        OpenAcdSkillGroupsBundleRestInfo skillGroupsBundleRestInfo =
                new OpenAcdSkillGroupsBundleRestInfo(skillGroupsRestInfo, metadataRestInfo);

        return new OpenAcdSkillGroupsRepresentation(variant.getMediaType(), skillGroupsBundleRestInfo);
    }

    // PUT - Update or Create single
    // -----------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        OpenAcdSkillGroupRepresentation representation = new OpenAcdSkillGroupRepresentation(entity);
        OpenAcdSkillGroupRestInfo skillGroupRestInfo = representation.getObject();
        OpenAcdSkillGroup skillGroup;

        // validate input for update or create
        ValidationInfo validationInfo = validate(skillGroupRestInfo);

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
                skillGroup = m_openAcdContext.getSkillGroupById(idInt);
            } catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), ResponseCode.ERROR_BAD_ID,
                        RestUtilities.getResponseMessage(ResponseCode.ERROR_BAD_ID, idString));
                return;
            }

            // copy values over to existing
            try {
                updateSkillGroup(skillGroup, skillGroupRestInfo);
                m_openAcdContext.saveSkillGroup(skillGroup);
            } catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), ResponseCode.ERROR_UPDATE_FAILED,
                        RestUtilities.getResponseMessage(ResponseCode.ERROR_UPDATE_FAILED, this.getClass()
                                .getSimpleName()), exception.getLocalizedMessage());
                return;
            }

            RestUtilities.setResponse(getResponse(), ResponseCode.SUCCESS_UPDATED, RestUtilities
                    .getResponseMessage(ResponseCode.SUCCESS_UPDATED, this.getClass().getSimpleName()),
                    skillGroup.getId());

            return;
        }

        // otherwise add new
        try {
            skillGroup = createSkillGroup(skillGroupRestInfo);
            m_openAcdContext.saveSkillGroup(skillGroup);
        } catch (Exception exception) {
            RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_CREATE_FAILED,
                    RestUtilities.getResponseMessage(ResponseCode.ERROR_CREATE_FAILED, this.getClass()
                            .getSimpleName()), exception.getLocalizedMessage());
            return;
        }

        RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_CREATED, RestUtilities
                .getResponseMessage(ResponseCode.SUCCESS_CREATED, this.getClass().getSimpleName()),
                skillGroup.getId());
    }

    // DELETE - Delete single
    // ----------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        OpenAcdSkillGroupRestInfo skillGroupRestInfo;
        List<OpenAcdSkill> skills;

        // skill groups are deleted by providing collection of ids, not by providing skill group
        // object
        Collection<Integer> skillGroupIds = new HashSet<Integer>();
        int idInt;

        // get id then delete single
        String idString = (String) getRequest().getAttributes().get(RestUtilities.REQUEST_ATTRIBUTE_ID);

        if (idString != null) {
            try {
                idInt = RestUtilities.getIntFromAttribute(idString);
                skillGroupIds.add(idInt);
            } catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_ID,
                        RestUtilities.getResponseMessage(ResponseCode.ERROR_BAD_ID, idString));
                return;
            }

            // sipxconfig ui does not allow delete of group with existing skills
            skills = m_openAcdContext.getSkills();
            for (OpenAcdSkill skill : skills) {
                if (skill.getGroup().getId() == idInt) {
                    RestUtilities.setResponseError(getResponse(), ResponseCode.ERROR_REFERENCE_EXISTS,
                            RestUtilities.getResponseMessage(ResponseCode.ERROR_REFERENCE_EXISTS,
                                    OpenAcdSkill.class.getSimpleName() + ":" + skill.getName()));
                    return;
                }
            }

            m_openAcdContext.removeSkillGroups(skillGroupIds);

            RestUtilities
                    .setResponse(getResponse(), ResponseCode.SUCCESS_DELETED,
                            RestUtilities.getResponseMessage(ResponseCode.SUCCESS_DELETED, this.getClass()
                                    .getSimpleName()), idInt);

            return;
        }

        // no id string
        RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.ERROR_MISSING_ID, RestUtilities
                .getResponseMessage(ResponseCode.ERROR_MISSING_ID, this.getClass().getSimpleName()));
    }

    // Helper functions
    // ----------------

    // basic interface level validation of data provided through REST interface for creation or
    // update
    // may also contain clean up of input data
    // may create another validation function if different rules needed for update v. create
    private ValidationInfo validate(OpenAcdSkillGroupRestInfo restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        String name = restInfo.getName();

        for (int i = 0; i < name.length(); i++) {
            if ((!Character.isLetterOrDigit(name.charAt(i)))
                    && (Character.getType(name.charAt(i)) != Character.CONNECTOR_PUNCTUATION)
                    && (name.charAt(i) != '-')) {
                validationInfo.setValid(false);
                validationInfo
                        .setMessage("Skill group name must only contain letters, numbers, dashes, and underscores");
                validationInfo.setResponseCode(ResponseCode.ERROR_BAD_ID);
            }
        }

        return validationInfo;
    }

    private OpenAcdSkillGroupRestInfo createSkillGroupRestInfo(int id) throws ResourceException {
        OpenAcdSkillGroupRestInfo skillGroupRestInfo;

        try {
            OpenAcdSkillGroup skillGroup = m_openAcdContext.getSkillGroupById(id);
            skillGroupRestInfo = new OpenAcdSkillGroupRestInfo(skillGroup);
        } catch (Exception exception) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, RestUtilities.getResponseMessage(
                    ResponseCode.ERROR_BAD_ID, Integer.toString(id)));
        }

        return skillGroupRestInfo;
    }

    private MetadataRestInfo addSkillGroups(List<OpenAcdSkillGroupRestInfo> skillsRestInfo,
            List<OpenAcdSkillGroup> skillGroups) {
        OpenAcdSkillGroupRestInfo skillRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = RestUtilities.calculatePagination(m_form, skillGroups.size());

        // create list of skill restinfos
        for (int index = paginationInfo.getStartIndex(); index <= paginationInfo.getEndIndex(); index++) {
            OpenAcdSkillGroup skillGroup = skillGroups.get(index);

            skillRestInfo = new OpenAcdSkillGroupRestInfo(skillGroup);
            skillsRestInfo.add(skillRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortSkillGroups(List<OpenAcdSkillGroup> skillGroups) {
        // sort groups if requested
        SortInfo sortInfo = RestUtilities.calculateSorting(m_form);

        if (!sortInfo.getSort()) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.getSortField());

        if (sortInfo.getDirectionForward()) {

            switch (sortField) {
            case NAME:
                Collections.sort(skillGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkillGroup skillGroup1 = (OpenAcdSkillGroup) object1;
                        OpenAcdSkillGroup skillGroup2 = (OpenAcdSkillGroup) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(skillGroup1.getName(),
                                skillGroup2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(skillGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkillGroup skillGroup1 = (OpenAcdSkillGroup) object1;
                        OpenAcdSkillGroup skillGroup2 = (OpenAcdSkillGroup) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(skillGroup1.getDescription(),
                                skillGroup2.getDescription());
                    }

                });
                break;

            case NUMBERSKILLS:
                Collections.sort(skillGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkillGroup skillGroup1 = (OpenAcdSkillGroup) object1;
                        OpenAcdSkillGroup skillGroup2 = (OpenAcdSkillGroup) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(
                                String.valueOf(skillGroup1.getSkills().size()),
                                String.valueOf(skillGroup2.getSkills().size()));
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
                Collections.sort(skillGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkillGroup skillGroup1 = (OpenAcdSkillGroup) object1;
                        OpenAcdSkillGroup skillGroup2 = (OpenAcdSkillGroup) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(skillGroup2.getName(),
                                skillGroup1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(skillGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkillGroup skillGroup1 = (OpenAcdSkillGroup) object1;
                        OpenAcdSkillGroup skillGroup2 = (OpenAcdSkillGroup) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(skillGroup2.getDescription(),
                                skillGroup1.getDescription());
                    }

                });
                break;

            case NUMBERSKILLS:
                Collections.sort(skillGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkillGroup skillGroup1 = (OpenAcdSkillGroup) object1;
                        OpenAcdSkillGroup skillGroup2 = (OpenAcdSkillGroup) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(
                                String.valueOf(skillGroup2.getSkills().size()),
                                String.valueOf(skillGroup1.getSkills().size()));
                    }

                });
                break;

            default:
                break;
            }
        }
    }

    private void updateSkillGroup(OpenAcdSkillGroup skillGroup, OpenAcdSkillGroupRestInfo skillGroupRestInfo)
        throws ResourceException {
        String tempString;

        // do not allow empty name
        tempString = skillGroupRestInfo.getName();
        if (!tempString.isEmpty()) {
            skillGroup.setName(tempString);
        }

        skillGroup.setDescription(skillGroupRestInfo.getDescription());
    }

    private OpenAcdSkillGroup createSkillGroup(OpenAcdSkillGroupRestInfo skillGroupRestInfo)
        throws ResourceException {
        OpenAcdSkillGroup skillGroup = new OpenAcdSkillGroup();

        // copy fields from rest info
        skillGroup.setName(skillGroupRestInfo.getName());
        skillGroup.setDescription(skillGroupRestInfo.getDescription());

        return skillGroup;
    }

    // REST Representations
    // --------------------

    static class OpenAcdSkillGroupsRepresentation extends
            XStreamRepresentation<OpenAcdSkillGroupsBundleRestInfo> {

        public OpenAcdSkillGroupsRepresentation(MediaType mediaType, OpenAcdSkillGroupsBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdSkillGroupsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias(ELEMENT_NAME_SKILLGROUPBUNDLE, OpenAcdSkillGroupsBundleRestInfo.class);
            xstream.alias(ELEMENT_NAME_SKILLGROUP, OpenAcdSkillGroupRestInfo.class);
        }
    }

    static class OpenAcdSkillGroupRepresentation extends XStreamRepresentation<OpenAcdSkillGroupRestInfo> {

        public OpenAcdSkillGroupRepresentation(MediaType mediaType, OpenAcdSkillGroupRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdSkillGroupRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias(ELEMENT_NAME_SKILLGROUP, OpenAcdSkillGroupRestInfo.class);
        }
    }

    // REST info objects
    // -----------------

    static class OpenAcdSkillGroupsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<OpenAcdSkillGroupRestInfo> m_groups;

        public OpenAcdSkillGroupsBundleRestInfo(List<OpenAcdSkillGroupRestInfo> skillGroups,
                MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_groups = skillGroups;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<OpenAcdSkillGroupRestInfo> getGroups() {
            return m_groups;
        }
    }

    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }

}
