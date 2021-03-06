/*
 *
 *  OpenAcdAgentGroupsResource.java - A Restlet to read group data from OpenACD within SipXecs
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
import java.util.Set;

import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sipfoundry.sipxconfig.openacd.OpenAcdAgentGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdClient;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.openacd.OpenAcdQueue;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkill;
import org.sipfoundry.sipxconfig.rest.RestUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdAgentGroupRestInfoFull;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdClientRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdQueueRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdSkillRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ResponseCode;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ValidationInfo;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class OpenAcdAgentGroupsResource extends UserResource {

    private OpenAcdContext m_openAcdContext;
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

    // GET - Retrieve Groups and single Group
    // --------------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        int idInt;
        OpenAcdAgentGroupRestInfoFull agentGroupRestInfo = null;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                idInt = RestUtilities.getIntFromAttribute(idString);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            try {
                agentGroupRestInfo = createAgentGroupRestInfo(idInt);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_READ_FAILED, "Read Agent Group failed", exception.getLocalizedMessage());
            }

            return new OpenAcdAgentGroupRepresentation(variant.getMediaType(), agentGroupRestInfo);
        }


        // if not single, process request for all
        List<OpenAcdAgentGroup> agentGroups = m_openAcdContext.getAgentGroups();
        List<OpenAcdAgentGroupRestInfoFull> agentGroupsRestInfo = new ArrayList<OpenAcdAgentGroupRestInfoFull>();
        MetadataRestInfo metadataRestInfo;

        // sort if specified
        sortGroups(agentGroups);

        // set requested based on pagination and get resulting metadata
        metadataRestInfo = addAgentGroups(agentGroupsRestInfo, agentGroups);

        // create final restinfo
        OpenAcdAgentGroupsBundleRestInfo agentGroupsBundleRestInfo = new OpenAcdAgentGroupsBundleRestInfo(agentGroupsRestInfo, metadataRestInfo);

        return new OpenAcdAgentGroupsRepresentation(variant.getMediaType(), agentGroupsBundleRestInfo);
    }


    // PUT - Update or Add single Group
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get group from body
        OpenAcdAgentGroupRepresentation representation = new OpenAcdAgentGroupRepresentation(entity);
        OpenAcdAgentGroupRestInfoFull agentGroupRestInfo = representation.getObject();
        OpenAcdAgentGroup agentGroup;

        // validate input for update or create
        ValidationInfo validationInfo = validate(agentGroupRestInfo);

        if (!validationInfo.valid) {
            RestUtilities.setResponseError(getResponse(), validationInfo.responseCode, validationInfo.message);
            return;
        }


        // if have id then update a single group
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = RestUtilities.getIntFromAttribute(idString);
                agentGroup = m_openAcdContext.getAgentGroupById(idInt);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            // copy values over to existing group
            try {
                updateAgentGroup(agentGroup, agentGroupRestInfo);
                m_openAcdContext.saveAgentGroup(agentGroup);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update Agent Group failed", exception.getLocalizedMessage());
                return;
            }

            RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_UPDATED, "Updated Agent Group", agentGroup.getId());

            return;
        }


        // otherwise add new agent group
        try {
            agentGroup = createAgentGroup(agentGroupRestInfo);
            m_openAcdContext.saveAgentGroup(agentGroup);
        }
        catch (Exception exception) {
            RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create Agent Group failed", exception.getLocalizedMessage());
            return;
        }

        RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_CREATED, "Created Agent Group", agentGroup.getId());
    }


    // DELETE - Delete single Group
    // --------------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        OpenAcdAgentGroup agentGroup;

        // get id then delete a single group
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = RestUtilities.getIntFromAttribute(idString);
                agentGroup = m_openAcdContext.getAgentGroupById(idInt);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            m_openAcdContext.deleteAgentGroup(agentGroup);

            RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_DELETED, "Deleted Agent Group", agentGroup.getId());

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
    private ValidationInfo validate(OpenAcdAgentGroupRestInfoFull restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        String name = restInfo.getName();

        for (int i = 0; i < name.length(); i++) {
            if ((!Character.isLetterOrDigit(name.charAt(i)) && !(Character.getType(name.charAt(i)) == Character.CONNECTOR_PUNCTUATION)) && name.charAt(i) != '-') {
                validationInfo.valid = false;
                validationInfo.message = "Validation Error: 'Name' must only contain letters, numbers, dashes, and underscores";
                validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
            }
        }

        return validationInfo;
    }

    private OpenAcdAgentGroupRestInfoFull createAgentGroupRestInfo(int id) throws ResourceException {
        OpenAcdAgentGroupRestInfoFull agentGroupRestInfo;
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        List<OpenAcdQueueRestInfo> queuesRestInfo;
        List<OpenAcdClientRestInfo> clientsRestInfo;

        OpenAcdAgentGroup agentGroup = m_openAcdContext.getAgentGroupById(id);

        skillsRestInfo = createSkillsRestInfo(agentGroup);
        queuesRestInfo = createQueuesRestInfo(agentGroup);
        clientsRestInfo = createClientsRestInfo(agentGroup);
        agentGroupRestInfo = new OpenAcdAgentGroupRestInfoFull(agentGroup, skillsRestInfo, queuesRestInfo, clientsRestInfo);

        return agentGroupRestInfo;
    }

    private MetadataRestInfo addAgentGroups(List<OpenAcdAgentGroupRestInfoFull> agentGroupsRestInfo, List<OpenAcdAgentGroup> agentGroups) {
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        List<OpenAcdQueueRestInfo> queuesRestInfo;
        List<OpenAcdClientRestInfo> clientsRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = RestUtilities.calculatePagination(m_form, agentGroups.size());

        // create list of group restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            OpenAcdAgentGroup agentGroup = agentGroups.get(index);
            skillsRestInfo = createSkillsRestInfo(agentGroup);
            queuesRestInfo = createQueuesRestInfo(agentGroup);
            clientsRestInfo = createClientsRestInfo(agentGroup);
            OpenAcdAgentGroupRestInfoFull agentGroupRestInfo = new OpenAcdAgentGroupRestInfoFull(agentGroup, skillsRestInfo, queuesRestInfo, clientsRestInfo);
            agentGroupsRestInfo.add(agentGroupRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private List<OpenAcdSkillRestInfo> createSkillsRestInfo(OpenAcdAgentGroup agentGroup) {
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        OpenAcdSkillRestInfo skillRestInfo;

        // create list of skill restinfos for single group
        Set<OpenAcdSkill> groupSkills = agentGroup.getSkills();
        skillsRestInfo = new ArrayList<OpenAcdSkillRestInfo>(groupSkills.size());

        for (OpenAcdSkill groupSkill : groupSkills) {
            skillRestInfo = new OpenAcdSkillRestInfo(groupSkill);
            skillsRestInfo.add(skillRestInfo);
        }

        return skillsRestInfo;
    }

    private List<OpenAcdQueueRestInfo> createQueuesRestInfo(OpenAcdAgentGroup agentGroup) {
        List<OpenAcdQueueRestInfo> queuesRestInfo;
        OpenAcdQueueRestInfo queueRestInfo;

        // create list of queue restinfos for single group
        Set<OpenAcdQueue> groupQueues = agentGroup.getQueues();
        queuesRestInfo = new ArrayList<OpenAcdQueueRestInfo>(groupQueues.size());

        for (OpenAcdQueue groupQueue : groupQueues) {
            queueRestInfo = new OpenAcdQueueRestInfo(groupQueue);
            queuesRestInfo.add(queueRestInfo);
        }

        return queuesRestInfo;
    }


    private List<OpenAcdClientRestInfo> createClientsRestInfo(OpenAcdAgentGroup agentGroup) {
        List<OpenAcdClientRestInfo> clientsRestInfo;
        OpenAcdClientRestInfo clientRestInfo;

        // create list of client restinfos for single group
        Set<OpenAcdClient> groupClients = agentGroup.getClients();
        clientsRestInfo = new ArrayList<OpenAcdClientRestInfo>(groupClients.size());

        for (OpenAcdClient groupClient : groupClients) {
            clientRestInfo = new OpenAcdClientRestInfo(groupClient);
            clientsRestInfo.add(clientRestInfo);
        }

        return clientsRestInfo;
    }

    private void sortGroups(List<OpenAcdAgentGroup> agentGroups) {
        // sort groups if requested
        SortInfo sortInfo = RestUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case NAME:
                Collections.sort(agentGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdAgentGroup agentGroup1 = (OpenAcdAgentGroup) object1;
                        OpenAcdAgentGroup agentGroup2 = (OpenAcdAgentGroup) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(agentGroup1.getName(), agentGroup2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(agentGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdAgentGroup agentGroup1 = (OpenAcdAgentGroup) object1;
                        OpenAcdAgentGroup agentGroup2 = (OpenAcdAgentGroup) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(agentGroup1.getDescription(),agentGroup2.getDescription());
                    }

                });
                break;
            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case NAME:
                Collections.sort(agentGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdAgentGroup agentGroup1 = (OpenAcdAgentGroup) object1;
                        OpenAcdAgentGroup agentGroup2 = (OpenAcdAgentGroup) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(agentGroup2.getName(), agentGroup1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(agentGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdAgentGroup agentGroup1 = (OpenAcdAgentGroup) object1;
                        OpenAcdAgentGroup agentGroup2 = (OpenAcdAgentGroup) object2;
                        return RestUtilities.compareIgnoreCaseNullSafe(agentGroup2.getDescription(),agentGroup1.getDescription());
                    }

                });
                break;
            }
        }
    }

    private void updateAgentGroup(OpenAcdAgentGroup agentGroup, OpenAcdAgentGroupRestInfoFull agentGroupRestInfo) {
        String tempString;

        // do not allow empty name
        tempString = agentGroupRestInfo.getName();
        if (!tempString.isEmpty()) {
            agentGroup.setName(tempString);
        }

        agentGroup.setDescription(agentGroupRestInfo.getDescription());

        addLists(agentGroup, agentGroupRestInfo);
    }

    private OpenAcdAgentGroup createAgentGroup(OpenAcdAgentGroupRestInfoFull agentGroupRestInfo) {
        OpenAcdAgentGroup agentGroup = new OpenAcdAgentGroup();

        // copy fields from rest info
        agentGroup.setName(agentGroupRestInfo.getName());
        agentGroup.setDescription(agentGroupRestInfo.getDescription());

        addLists(agentGroup, agentGroupRestInfo);

        return agentGroup;
    }

    private void addLists(OpenAcdAgentGroup agentGroup, OpenAcdAgentGroupRestInfoFull agentGroupRestInfo) {
        // remove all current skills
        agentGroup.getSkills().clear();

        // set skills
        OpenAcdSkill skill;
        List<OpenAcdSkillRestInfo> skillsRestInfo = agentGroupRestInfo.getSkills();
        for (OpenAcdSkillRestInfo skillRestInfo : skillsRestInfo) {
            skill = m_openAcdContext.getSkillById(skillRestInfo.getId());
            agentGroup.addSkill(skill);
        }

        // remove all current queues
        agentGroup.getQueues().clear();

        // set queues
        OpenAcdQueue queue;
        List<OpenAcdQueueRestInfo> queuesRestInfo = agentGroupRestInfo.getQueues();
        for (OpenAcdQueueRestInfo queueRestInfo : queuesRestInfo) {
            queue = m_openAcdContext.getQueueById(queueRestInfo.getId());
            agentGroup.addQueue(queue);
        }

        // remove all current clients
        agentGroup.getClients().clear();

        // set clients
        OpenAcdClient client;
        List<OpenAcdClientRestInfo> clientsRestInfo = agentGroupRestInfo.getClients();
        for (OpenAcdClientRestInfo clientRestInfo : clientsRestInfo) {
            client = m_openAcdContext.getClientById(clientRestInfo.getId());
            agentGroup.addClient(client);
        }
    }


    // REST Representations
    // --------------------

    static class OpenAcdAgentGroupsRepresentation extends XStreamRepresentation<OpenAcdAgentGroupsBundleRestInfo> {

        public OpenAcdAgentGroupsRepresentation(MediaType mediaType, OpenAcdAgentGroupsBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdAgentGroupsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("openacd-agent-group", OpenAcdAgentGroupsBundleRestInfo.class);
            xstream.alias("group", OpenAcdAgentGroupRestInfoFull.class);
            xstream.alias("skill", OpenAcdSkillRestInfo.class);
            xstream.alias("queue", OpenAcdQueueRestInfo.class);
            xstream.alias("client", OpenAcdClientRestInfo.class);
        }
    }

    static class OpenAcdAgentGroupRepresentation extends XStreamRepresentation<OpenAcdAgentGroupRestInfoFull> {

        public OpenAcdAgentGroupRepresentation(MediaType mediaType, OpenAcdAgentGroupRestInfoFull object) {
            super(mediaType, object);
        }

        public OpenAcdAgentGroupRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("group", OpenAcdAgentGroupRestInfoFull.class);
            xstream.alias("skill", OpenAcdSkillRestInfo.class);
            xstream.alias("queue", OpenAcdQueueRestInfo.class);
            xstream.alias("client", OpenAcdClientRestInfo.class);
        }
    }


    // REST info objects
    // -----------------

    static class OpenAcdAgentGroupsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<OpenAcdAgentGroupRestInfoFull> m_groups;

        public OpenAcdAgentGroupsBundleRestInfo(List<OpenAcdAgentGroupRestInfoFull> agentGroups, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_groups = agentGroups;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<OpenAcdAgentGroupRestInfoFull> getGroups() {
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
