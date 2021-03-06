/*
 *
 *  OpenAcdAgentGroupsResource.java - A Restlet to read Skill data from OpenACD within SipXecs
 *  Copyright (C) 2012 PATLive, D. Chang
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
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkill;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkillGroup;
import org.sipfoundry.sipxconfig.rest.RestUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdSkillRestInfoFull;
import org.sipfoundry.sipxconfig.rest.RestUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ResponseCode;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ValidationInfo;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class OpenAcdSkillsResource extends UserResource {

    private OpenAcdContext m_openAcdContext;
    private Form m_form;

    // use to define all possible sort fields
    private enum SortField {
        NAME, DESCRIPTION, ATOM, GROUPNAME, NONE;

        public static SortField toSortField(String fieldString) {
            if (fieldString == null) {
                return NONE;
            }

            try {
                return valueOf(fieldString.toUpperCase());
            }
            catch (Exception ex) {
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
        OpenAcdSkillRestInfoFull skillRestInfo = null;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                idInt = RestUtilities.getIntFromAttribute(idString);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            try {
                skillRestInfo = createSkillRestInfo(idInt);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_READ_FAILED, "Read Skills failed", exception.getLocalizedMessage());
            }

            return new OpenAcdSkillRepresentation(variant.getMediaType(), skillRestInfo);
        }


        // if not single, process request for all
        List<OpenAcdSkill> skills = m_openAcdContext.getSkills();
        List<OpenAcdSkillRestInfoFull> skillsRestInfo = new ArrayList<OpenAcdSkillRestInfoFull>();
        MetadataRestInfo metadataRestInfo;

        // sort groups if specified
        sortSkills(skills);

        // set requested agents groups and get resulting metadata
        metadataRestInfo = addSkills(skillsRestInfo, skills);

        // create final restinfo
        OpenAcdSkillsBundleRestInfo skillsBundleRestInfo = new OpenAcdSkillsBundleRestInfo(skillsRestInfo, metadataRestInfo);

        return new OpenAcdSkillsRepresentation(variant.getMediaType(), skillsBundleRestInfo);
    }


    // PUT - Update or Add single Skill
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        OpenAcdSkillRepresentation representation = new OpenAcdSkillRepresentation(entity);
        OpenAcdSkillRestInfoFull skillRestInfo = representation.getObject();
        OpenAcdSkill skill = null;

        // validate input for update or create
        ValidationInfo validationInfo = validate(skillRestInfo);

        if (!validationInfo.valid) {
            RestUtilities.setResponseError(getResponse(), validationInfo.responseCode, validationInfo.message);
            return;
        }


        // if have id then update single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = RestUtilities.getIntFromAttribute(idString);
                skill = m_openAcdContext.getSkillById(idInt);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            // copy values over to existing
            try {
                updateSkill(skill, skillRestInfo);
                m_openAcdContext.saveSkill(skill);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update Skill failed", exception.getLocalizedMessage());
                return;
            }

            RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_UPDATED, "Updated Skill", skill.getId());

            return;
        }


        // otherwise add new
        try {
            skill = createSkill(skillRestInfo);
            m_openAcdContext.saveSkill(skill);
        }
        catch (Exception exception) {
            RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create Skill failed", exception.getLocalizedMessage());
            return;
        }

        RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_CREATED, "Created Skill", skill.getId());
    }


    // DELETE - Delete single Skill
    // ----------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        OpenAcdSkill skill;

        // get id then delete single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = RestUtilities.getIntFromAttribute(idString);
                skill = m_openAcdContext.getSkillById(idInt);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            m_openAcdContext.deleteSkill(skill);

            RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_DELETED, "Deleted Skill", skill.getId());

            return;
        }

        // no id string
        RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.ERROR_MISSING_INPUT, "ID value missing");
    }


    // Helper functions
    // ----------------

    // basic interface level validation of data provided through REST interface for creation or
    // update
    // may also contain clean up of input data
    // may create another validation function if different rules needed for update v. create
    private ValidationInfo validate(OpenAcdSkillRestInfoFull restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        String name = restInfo.getName();
        String atom = restInfo.getAtom();

        for (int i = 0; i < name.length(); i++) {
            if ((!Character.isLetterOrDigit(name.charAt(i)) && !(Character.getType(name.charAt(i)) == Character.CONNECTOR_PUNCTUATION)) && name.charAt(i) != '-') {
                validationInfo.valid = false;
                validationInfo.message = "Validation Error: Skill Group 'Name' must only contain letters, numbers, dashes, and underscores";
                validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
            }
        }

        for (int i = 0; i < atom.length(); i++) {
            if ((!Character.isLetterOrDigit(atom.charAt(i)) && !(Character.getType(atom.charAt(i)) == Character.CONNECTOR_PUNCTUATION)) && atom.charAt(i) != '-') {
                validationInfo.valid = false;
                validationInfo.message = "Validation Error: 'Atom' must only contain letters, numbers, dashes, and underscores";
                validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
            }
        }

        return validationInfo;
    }

    private OpenAcdSkillRestInfoFull createSkillRestInfo(int id) throws ResourceException {
        OpenAcdSkillRestInfoFull skillRestInfo = null;

        OpenAcdSkill skill = m_openAcdContext.getSkillById(id);
        skillRestInfo = new OpenAcdSkillRestInfoFull(skill);

        return skillRestInfo;
    }

    private MetadataRestInfo addSkills(List<OpenAcdSkillRestInfoFull> skillsRestInfo, List<OpenAcdSkill> skills) {
        OpenAcdSkillRestInfoFull skillRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = RestUtilities.calculatePagination(m_form, skills.size());

        // create list of skill restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            OpenAcdSkill skill = skills.get(index);

            skillRestInfo = new OpenAcdSkillRestInfoFull(skill);
            skillsRestInfo.add(skillRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortSkills(List<OpenAcdSkill> skills) {
        // sort groups if requested
        SortInfo sortInfo = RestUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case NAME:
                Collections.sort(skills, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkill skill1 = (OpenAcdSkill) object1;
                        OpenAcdSkill skill2 = (OpenAcdSkill) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(skill1.getName(), skill2.getName());
                    }

                });
                break;
                
            case GROUPNAME:
                Collections.sort(skills, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkill skill1 = (OpenAcdSkill) object1;
                        OpenAcdSkill skill2 = (OpenAcdSkill) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(skill1.getGroupName(), skill2.getGroupName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(skills, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkill skill1 = (OpenAcdSkill) object1;
                        OpenAcdSkill skill2 = (OpenAcdSkill) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(skill1.getDescription(), skill2.getDescription());
                    }

                });
                break;


            case ATOM:
                Collections.sort(skills, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkill skill1 = (OpenAcdSkill) object1;
                        OpenAcdSkill skill2 = (OpenAcdSkill) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(skill1.getAtom(), skill2.getAtom());
                    }

                });
                break;
            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case NAME:
                Collections.sort(skills, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkill skill1 = (OpenAcdSkill) object1;
                        OpenAcdSkill skill2 = (OpenAcdSkill) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(skill2.getName(), skill1.getName());
                    }

                });
                break;
                
            case GROUPNAME:
                Collections.sort(skills, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkill skill1 = (OpenAcdSkill) object1;
                        OpenAcdSkill skill2 = (OpenAcdSkill) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(skill2.getGroupName(), skill1.getGroupName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(skills, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkill skill1 = (OpenAcdSkill) object1;
                        OpenAcdSkill skill2 = (OpenAcdSkill) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(skill2.getDescription(), skill1.getDescription());
                    }

                });
                break;

            case ATOM:
                Collections.sort(skills, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkill skill1 = (OpenAcdSkill) object1;
                        OpenAcdSkill skill2 = (OpenAcdSkill) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(skill2.getAtom(), skill1.getAtom());
                    }

                });
                break;
            }
        }
    }

    private void updateSkill(OpenAcdSkill skill, OpenAcdSkillRestInfoFull skillRestInfo) {
        OpenAcdSkillGroup skillGroup;
        String tempString;

        // do not allow empty name
        tempString = skillRestInfo.getName();
        if (!tempString.isEmpty()) {
            skill.setName(tempString);
        }

        skill.setDescription(skillRestInfo.getDescription());

        skillGroup = getSkillGroup(skillRestInfo);
        skill.setGroup(skillGroup);
    }

    private OpenAcdSkill createSkill(OpenAcdSkillRestInfoFull skillRestInfo) throws ResourceException {
        OpenAcdSkillGroup skillGroup;
        OpenAcdSkill skill = new OpenAcdSkill();

        // copy fields from rest info
        skill.setName(skillRestInfo.getName());
        skill.setDescription(skillRestInfo.getDescription());
        skill.setAtom(skillRestInfo.getAtom());

        skillGroup = getSkillGroup(skillRestInfo);
        skill.setGroup(skillGroup);

        return skill;
    }

    private OpenAcdSkillGroup getSkillGroup(OpenAcdSkillRestInfoFull skillRestInfo) {
        OpenAcdSkillGroup skillGroup;
        int groupId = skillRestInfo.getGroupId();
        skillGroup = m_openAcdContext.getSkillGroupById(groupId);

        return skillGroup;
    }


    // REST Representations
    // --------------------

    static class OpenAcdSkillsRepresentation extends XStreamRepresentation<OpenAcdSkillsBundleRestInfo> {

        public OpenAcdSkillsRepresentation(MediaType mediaType, OpenAcdSkillsBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdSkillsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("openacd-skill", OpenAcdSkillsBundleRestInfo.class);
            xstream.alias("skill", OpenAcdSkillRestInfoFull.class);
        }
    }

    static class OpenAcdSkillRepresentation extends XStreamRepresentation<OpenAcdSkillRestInfoFull> {

        public OpenAcdSkillRepresentation(MediaType mediaType, OpenAcdSkillRestInfoFull object) {
            super(mediaType, object);
        }

        public OpenAcdSkillRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("skill", OpenAcdSkillRestInfoFull.class);
        }
    }


    // REST info objects
    // -----------------

    static class OpenAcdSkillsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<OpenAcdSkillRestInfoFull> m_skills;

        public OpenAcdSkillsBundleRestInfo(List<OpenAcdSkillRestInfoFull> skills, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_skills = skills;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<OpenAcdSkillRestInfoFull> getSkills() {
            return m_skills;
        }
    }


    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }

}
