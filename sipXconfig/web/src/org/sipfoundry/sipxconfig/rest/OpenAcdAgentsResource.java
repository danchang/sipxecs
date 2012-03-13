/*
 *
 *  Copyright (C) 2012 PATLive, D. Waseem, D. Chang
 *  Contributed to SIPfoundry under a Contributor Agreement
 *  OpenAcdAgentGroupsResource.java - A Restlet to read Agent data from OpenACD within SipXecs
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.openacd.OpenAcdAgent;
import org.sipfoundry.sipxconfig.openacd.OpenAcdAgentGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdClient;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.openacd.OpenAcdQueue;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkill;
import org.sipfoundry.sipxconfig.rest.RestUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdAgentGroupRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdAgentRestInfoFull;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdClientRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdQueueRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.OpenAcdSkillRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ResponseCode;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ValidationInfo;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class OpenAcdAgentsResource extends UserResource {

    private static final String ELEMENT_NAME_AGENTBUNDLE = "openacd-agent";
    private static final String ELEMENT_NAME_AGENT = "agent";
    private static final String ELEMENT_NAME_GROUP = "group";
    private static final String ELEMENT_NAME_SKILL = "skill";
    private static final String ELEMENT_NAME_QUEUE = "queue";
    private static final String ELEMENT_NAME_CLIENT = "client";

    private OpenAcdContext m_openAcdContext;
    private CoreContext m_coreContext;
    private Form m_form;

    // use to define all possible sort fields
    private enum SortField {
        NAME, GROUP, SECURITY, USERNAME, FIRSTNAME, LASTNAME, BRANCH, EXTENSION, NONE;

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

        m_coreContext = getCoreContext();

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

    // GET - Retrieve all and single Agent
    // -----------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        int idInt;
        OpenAcdAgentRestInfoFull agentRestInfo = null;
        String idString = (String) getRequest().getAttributes().get(RestUtilities.REQUEST_ATTRIBUTE_ID);

        if (idString != null) {
            try {
                idInt = RestUtilities.getIntFromAttribute(idString);
                agentRestInfo = createAgentRestInfo(idInt);
            } catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), ResponseCode.ERROR_BAD_ID, RestUtilities
                        .getResponseMessage(ResponseCode.ERROR_BAD_ID, idString));
            }

            try {
                agentRestInfo = createAgentRestInfo(idInt);
            } catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), ResponseCode.ERROR_READ_FAILED,
                        RestUtilities.getResponseMessage(ResponseCode.ERROR_READ_FAILED, this.getClass()
                                .getSimpleName()), exception.getLocalizedMessage());
            }

            return new OpenAcdAgentRepresentation(variant.getMediaType(), agentRestInfo);
        }

        // if not single, process request for all
        List<OpenAcdAgent> agents = m_openAcdContext.getAgents();
        List<OpenAcdAgentRestInfoFull> agentsRestInfo = new ArrayList<OpenAcdAgentRestInfoFull>();
        MetadataRestInfo metadataRestInfo;

        // sort if specified
        sortAgents(agents);

        // set requested agents groups and get resulting metadata
        metadataRestInfo = addAgents(agentsRestInfo, agents);

        // create final restinfo
        OpenAcdAgentsBundleRestInfo agentsBundleRestInfo =
                new OpenAcdAgentsBundleRestInfo(agentsRestInfo, metadataRestInfo);

        return new OpenAcdAgentsRepresentation(variant.getMediaType(), agentsBundleRestInfo);
    }

    // PUT - Update or Add single Agent
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        OpenAcdAgentRepresentation representation = new OpenAcdAgentRepresentation(entity);
        OpenAcdAgentRestInfoFull agentRestInfo = (OpenAcdAgentRestInfoFull) representation.getObject();
        OpenAcdAgent agent;

        // validate input for update or create
        ValidationInfo validationInfo = validate(agentRestInfo);

        if (!validationInfo.getValid()) {
            RestUtilities.setResponseError(getResponse(), validationInfo.getResponseCode(), validationInfo
                    .getMessage());
            return;
        }

        // if have id then update single
        String idString = (String) getRequest().getAttributes().get(RestUtilities.REQUEST_ATTRIBUTE_ID);

        if (idString != null) {
            try {
                int idInt = RestUtilities.getIntFromAttribute(idString);
                agent = m_openAcdContext.getAgentById(idInt);
            } catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), ResponseCode.ERROR_BAD_ID, RestUtilities
                        .getResponseMessage(ResponseCode.ERROR_BAD_ID, idString));
                return;
            }

            // copy values over to existing
            try {
                updateAgent(agent, agentRestInfo);
                m_openAcdContext.saveAgent(agent);
            } catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), ResponseCode.ERROR_UPDATE_FAILED,
                        RestUtilities.getResponseMessage(ResponseCode.ERROR_UPDATE_FAILED, this.getClass()
                                .getSimpleName()), exception.getLocalizedMessage());
                return;
            }

            RestUtilities.setResponse(getResponse(), ResponseCode.SUCCESS_UPDATED, RestUtilities
                    .getResponseMessage(ResponseCode.SUCCESS_UPDATED, this.getClass().getSimpleName()), agent
                    .getId());
            return;
        }

        // otherwise add new
        try {
            agent = createOpenAcdAgent(agentRestInfo);
            m_openAcdContext.saveAgent(agent);
        } catch (Exception exception) {
            RestUtilities.setResponseError(getResponse(), ResponseCode.ERROR_UPDATE_FAILED, RestUtilities
                    .getResponseMessage(ResponseCode.ERROR_CREATE_FAILED, this.getClass().getSimpleName()),
                    exception.getLocalizedMessage());
            return;
        }

        RestUtilities.setResponse(getResponse(), ResponseCode.SUCCESS_CREATED, RestUtilities
                .getResponseMessage(ResponseCode.SUCCESS_CREATED, this.getClass().getSimpleName()), agent
                .getId());
    }

    // DELETE - Delete single Agent
    // ----------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        OpenAcdAgent agent;

        // get id then delete single
        String idString = (String) getRequest().getAttributes().get(RestUtilities.REQUEST_ATTRIBUTE_ID);

        if (idString != null) {
            try {
                int idInt = RestUtilities.getIntFromAttribute(idString);
                agent = m_openAcdContext.getAgentById(idInt);
            } catch (Exception ex) {
                RestUtilities.setResponseError(getResponse(), ResponseCode.ERROR_BAD_ID, RestUtilities
                        .getResponseMessage(ResponseCode.ERROR_BAD_ID, idString));
                return;
            }

            m_openAcdContext.deleteAgent(agent);

            RestUtilities.setResponse(getResponse(), ResponseCode.SUCCESS_DELETED, RestUtilities
                    .getResponseMessage(ResponseCode.SUCCESS_DELETED, this.getClass().getSimpleName()), agent
                    .getId());

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
    private ValidationInfo validate(OpenAcdAgentRestInfoFull restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        return validationInfo;
    }

    private OpenAcdAgentRestInfoFull createAgentRestInfo(int id) throws ResourceException {
        OpenAcdAgentRestInfoFull agentRestInfo;
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        List<OpenAcdQueueRestInfo> queuesRestInfo;
        List<OpenAcdClientRestInfo> clientsRestInfo;

        OpenAcdAgent agent = m_openAcdContext.getAgentById(id);

        skillsRestInfo = createSkillsRestInfo(agent);
        queuesRestInfo = createQueuesRestInfo(agent);
        clientsRestInfo = createClientRestInfo(agent);
        agentRestInfo = new OpenAcdAgentRestInfoFull(agent, skillsRestInfo, queuesRestInfo, clientsRestInfo);

        return agentRestInfo;
    }

    private MetadataRestInfo addAgents(List<OpenAcdAgentRestInfoFull> agentsRestInfo,
            List<OpenAcdAgent> agents) {
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        List<OpenAcdQueueRestInfo> queuesRestInfo;
        List<OpenAcdClientRestInfo> clientsRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = RestUtilities.calculatePagination(m_form, agents.size());

        // create list of agent restinfos
        for (int index = paginationInfo.getStartIndex(); index <= paginationInfo.getEndIndex(); index++) {
            OpenAcdAgent agent = agents.get(index);
            skillsRestInfo = createSkillsRestInfo(agent);
            queuesRestInfo = createQueuesRestInfo(agent);
            clientsRestInfo = createClientRestInfo(agent);

            OpenAcdAgentRestInfoFull agentRestInfo =
                    new OpenAcdAgentRestInfoFull(agent, skillsRestInfo, queuesRestInfo, clientsRestInfo);
            agentsRestInfo.add(agentRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private List<OpenAcdSkillRestInfo> createSkillsRestInfo(OpenAcdAgent agent) {
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        OpenAcdSkillRestInfo skillRestInfo;

        // create list of skill restinfos for single group
        Set<OpenAcdSkill> groupSkills = agent.getSkills();
        skillsRestInfo = new ArrayList<OpenAcdSkillRestInfo>(groupSkills.size());

        for (OpenAcdSkill groupSkill : groupSkills) {
            skillRestInfo = new OpenAcdSkillRestInfo(groupSkill);
            skillsRestInfo.add(skillRestInfo);
        }

        return skillsRestInfo;
    }

    private List<OpenAcdQueueRestInfo> createQueuesRestInfo(OpenAcdAgent agent) {
        List<OpenAcdQueueRestInfo> queuesRestInfo;
        OpenAcdQueueRestInfo queueRestInfo;

        // create list of queue restinfos for single group
        Set<OpenAcdQueue> groupQueues = agent.getQueues();
        queuesRestInfo = new ArrayList<OpenAcdQueueRestInfo>(groupQueues.size());

        for (OpenAcdQueue groupQueue : groupQueues) {
            queueRestInfo = new OpenAcdQueueRestInfo(groupQueue);
            queuesRestInfo.add(queueRestInfo);
        }

        return queuesRestInfo;
    }

    private List<OpenAcdClientRestInfo> createClientRestInfo(OpenAcdAgent agent) {
        List<OpenAcdClientRestInfo> clientsRestInfo;
        OpenAcdClientRestInfo clientRestInfo;

        // create list of queue restinfos for single group
        Set<OpenAcdClient> groupClients = agent.getClients();
        clientsRestInfo = new ArrayList<OpenAcdClientRestInfo>(groupClients.size());

        for (OpenAcdClient groupClient : groupClients) {
            clientRestInfo = new OpenAcdClientRestInfo(groupClient);
            clientsRestInfo.add(clientRestInfo);
        }

        return clientsRestInfo;

    }

    private void sortAgents(List<OpenAcdAgent> agents) {
        // sort groups if requested
        SortInfo sortInfo = RestUtilities.calculateSorting(m_form);

        if (!sortInfo.getSort()) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.getSortField());

        switch (sortField) {
        case NAME:
            sortAgentsByName(agents, sortInfo.getDirectionForward());
            break;

        case GROUP:
            sortAgentsByGroup(agents, sortInfo.getDirectionForward());
            break;

        case SECURITY:
            sortAgentsBySecurity(agents, sortInfo.getDirectionForward());
            break;

        case USERNAME:
            sortAgentsByUserName(agents, sortInfo.getDirectionForward());
            break;

        case FIRSTNAME:
            sortAgentsByFirstName(agents, sortInfo.getDirectionForward());
            break;

        case LASTNAME:
            sortAgentsByLastName(agents, sortInfo.getDirectionForward());
            break;

        case BRANCH:
            sortAgentsByBranch(agents, sortInfo.getDirectionForward());
            break;

        case EXTENSION:
            sortAgentsByExtension(agents, sortInfo.getDirectionForward());
            break;

        default:
            break;
        }
    }

    @SuppressWarnings("unchecked")
    private void sortAgentsByName(List<OpenAcdAgent> agents, boolean directionForward) {
        if (directionForward) {
            Collections.sort(agents, new Comparator() {

                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;

                    return RestUtilities.compareIgnoreCaseNullSafe(agent1.getName(), agent2.getName());
                }

            });
        } else {
            Collections.sort(agents, new Comparator() {

                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;

                    return RestUtilities.compareIgnoreCaseNullSafe(agent2.getName(), agent1.getName());
                }

            });
        }
    }

    @SuppressWarnings("unchecked")
    private void sortAgentsByGroup(List<OpenAcdAgent> agents, boolean directionForward) {
        if (directionForward) {
            Collections.sort(agents, new Comparator() {
                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;

                    return RestUtilities.compareIgnoreCaseNullSafe(agent1.getAgentGroup(), agent2
                            .getAgentGroup());
                }
            });
        } else {
            Collections.sort(agents, new Comparator() {
                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;

                    return RestUtilities.compareIgnoreCaseNullSafe(agent2.getAgentGroup(), agent1
                            .getAgentGroup());
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void sortAgentsBySecurity(List<OpenAcdAgent> agents, boolean directionForward) {
        if (directionForward) {
            Collections.sort(agents, new Comparator() {

                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;
                    return RestUtilities.compareIgnoreCaseNullSafe(agent1.getUser().getGroupsNames(), agent2
                            .getUser().getGroupsNames());
                }

            });
        } else {
            Collections.sort(agents, new Comparator() {

                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;
                    return RestUtilities.compareIgnoreCaseNullSafe(agent2.getUser().getGroupsNames(), agent1
                            .getUser().getGroupsNames());
                }

            });
        }
    }

    @SuppressWarnings("unchecked")
    private void sortAgentsByUserName(List<OpenAcdAgent> agents, boolean directionForward) {
        if (directionForward) {
            Collections.sort(agents, new Comparator() {

                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;
                    return RestUtilities.compareIgnoreCaseNullSafe(agent1.getUser().getUserName(), agent2
                            .getUser().getUserName());
                }

            });
        } else {
            Collections.sort(agents, new Comparator() {

                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;
                    return RestUtilities.compareIgnoreCaseNullSafe(agent2.getUser().getUserName(), agent1
                            .getUser().getUserName());
                }

            });
        }
    }

    @SuppressWarnings("unchecked")
    private void sortAgentsByFirstName(List<OpenAcdAgent> agents, boolean directionForward) {
        if (directionForward) {
            Collections.sort(agents, new Comparator() {

                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;
                    return RestUtilities.compareIgnoreCaseNullSafe(agent1.getFirstName(), agent2
                            .getFirstName());
                }

            });
        } else {
            Collections.sort(agents, new Comparator() {

                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;
                    return RestUtilities.compareIgnoreCaseNullSafe(agent2.getFirstName(), agent1
                            .getFirstName());
                }

            });
        }
    }

    @SuppressWarnings("unchecked")
    private void sortAgentsByLastName(List<OpenAcdAgent> agents, boolean directionForward) {
        if (directionForward) {
            Collections.sort(agents, new Comparator() {

                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;
                    return RestUtilities
                            .compareIgnoreCaseNullSafe(agent1.getLastName(), agent2.getLastName());
                }

            });
        } else {
            Collections.sort(agents, new Comparator() {

                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;
                    return RestUtilities
                            .compareIgnoreCaseNullSafe(agent2.getLastName(), agent1.getLastName());
                }

            });
        }
    }

    @SuppressWarnings("unchecked")
    private void sortAgentsByBranch(List<OpenAcdAgent> agents, boolean directionForward) {
        if (directionForward) {
            Collections.sort(agents, new Comparator() {

                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;
                    return RestUtilities.compareIgnoreCaseNullSafe(agent1.getUser().getBranch().getName(),
                            agent2.getUser().getBranch().getName());
                }

            });
        } else {
            Collections.sort(agents, new Comparator() {

                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;
                    return RestUtilities.compareIgnoreCaseNullSafe(agent2.getUser().getBranch().getName(),
                            agent1.getUser().getBranch().getName());
                }

            });
        }
    }

    @SuppressWarnings("unchecked")
    private void sortAgentsByExtension(List<OpenAcdAgent> agents, boolean directionForward) {
        if (directionForward) {
            Collections.sort(agents, new Comparator() {

                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;
                    return RestUtilities.compareIgnoreCaseNullSafe(agent1.getUser().getExtension(true),
                            agent2.getUser().getExtension(true));
                }

            });
        } else {
            Collections.sort(agents, new Comparator() {

                public int compare(Object object1, Object object2) {
                    OpenAcdAgent agent1 = (OpenAcdAgent) object1;
                    OpenAcdAgent agent2 = (OpenAcdAgent) object2;
                    return RestUtilities.compareIgnoreCaseNullSafe(agent2.getUser().getExtension(true),
                            agent1.getUser().getExtension(true));
                }

            });
        }
    }

    private void updateAgent(OpenAcdAgent agent, OpenAcdAgentRestInfoFull agentRestInfo)
        throws ResourceException {
        OpenAcdAgentGroup agentGroup;

        agentGroup = getAgentGroup(agentRestInfo);
        agent.setGroup(agentGroup);

        agent.setSecurity(agentRestInfo.getSecurity());

        agent.getSkills().clear();

        OpenAcdSkill skill;
        List<OpenAcdSkillRestInfo> skillsRestInfo = agentRestInfo.getSkills();
        for (OpenAcdSkillRestInfo skillRestInfo : skillsRestInfo) {
            skill = m_openAcdContext.getSkillById(skillRestInfo.getId());
            agent.addSkill(skill);
        }

        agent.getQueues().clear();

        OpenAcdQueue queue;
        List<OpenAcdQueueRestInfo> queuesRestInfo = agentRestInfo.getQueues();
        for (OpenAcdQueueRestInfo queueRestInfo : queuesRestInfo) {
            queue = m_openAcdContext.getQueueById(queueRestInfo.getId());
            agent.addQueue(queue);
        }

        agent.getClients().clear();

        OpenAcdClient client;
        List<OpenAcdClientRestInfo> clientsRestInfo = agentRestInfo.getClients();
        for (OpenAcdClientRestInfo clientRestInfo : clientsRestInfo) {
            client = m_openAcdContext.getClientById(clientRestInfo.getId());
            agent.addClient(client);
        }

    }

    private OpenAcdAgent createOpenAcdAgent(OpenAcdAgentRestInfoFull agentRestInfo) throws ResourceException {
        OpenAcdAgent agent = new OpenAcdAgent();
        OpenAcdAgent duplicateAgent = null;
        OpenAcdAgentGroup agentGroup;
        User user;

        agentGroup = getAgentGroup(agentRestInfo);
        agent.setGroup(agentGroup);

        agent.setSecurity(agentRestInfo.getSecurity());

        user = m_coreContext.getUser(agentRestInfo.getUserId());

        // check if user is already assigned as agent
        duplicateAgent = m_openAcdContext.getAgentByUser(user);
        if (duplicateAgent != null) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "User " + user.getId()
                    + " already assigned as agent.");
        }

        agent.setUser(user);

        Set<OpenAcdSkill> skills = new LinkedHashSet<OpenAcdSkill>();
        List<OpenAcdSkillRestInfo> skillsRestInfo = agentRestInfo.getSkills();

        for (OpenAcdSkillRestInfo skillRestInfo : skillsRestInfo) {
            skills.add(m_openAcdContext.getSkillById(skillRestInfo.getId()));
        }

        Set<OpenAcdQueue> queues = new LinkedHashSet<OpenAcdQueue>();
        List<OpenAcdQueueRestInfo> queuesRestInfo = agentRestInfo.getQueues();

        for (OpenAcdQueueRestInfo queueRestInfo : queuesRestInfo) {
            queues.add(m_openAcdContext.getQueueById(queueRestInfo.getId()));
        }

        Set<OpenAcdClient> clients = new LinkedHashSet<OpenAcdClient>();
        List<OpenAcdClientRestInfo> clientsRestInfo = agentRestInfo.getClients();

        for (OpenAcdClientRestInfo clientRestInfo : clientsRestInfo) {
            clients.add(m_openAcdContext.getClientById(clientRestInfo.getId()));
        }

        agent.setSkills(skills);
        agent.setQueues(queues);
        agent.setClients(clients);

        return agent;
    }

    private OpenAcdAgentGroup getAgentGroup(OpenAcdAgentRestInfoFull agentRestInfo) throws ResourceException {
        OpenAcdAgentGroup agentGroup;
        int groupId = 0;

        try {
            groupId = agentRestInfo.getGroupId();
            agentGroup = m_openAcdContext.getAgentGroupById(groupId);
        } catch (Exception ex) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, RestUtilities.getResponseMessage(
                    ResponseCode.ERROR_MISSING_ID, OpenAcdAgentGroup.class.getSimpleName()));
        }

        return agentGroup;
    }

    // REST Representations
    // --------------------

    static class OpenAcdAgentsRepresentation extends XStreamRepresentation<OpenAcdAgentsBundleRestInfo> {

        public OpenAcdAgentsRepresentation(MediaType mediaType, OpenAcdAgentsBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdAgentsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias(ELEMENT_NAME_AGENTBUNDLE, OpenAcdAgentsBundleRestInfo.class);
            xstream.alias(ELEMENT_NAME_AGENT, OpenAcdAgentRestInfoFull.class);
            xstream.alias(ELEMENT_NAME_GROUP, OpenAcdAgentGroupRestInfo.class);
            xstream.alias(ELEMENT_NAME_SKILL, OpenAcdSkillRestInfo.class);
            xstream.alias(ELEMENT_NAME_QUEUE, OpenAcdQueueRestInfo.class);
            xstream.alias(ELEMENT_NAME_CLIENT, OpenAcdClientRestInfo.class);
        }
    }

    static class OpenAcdAgentRepresentation extends XStreamRepresentation<OpenAcdAgentRestInfoFull> {

        public OpenAcdAgentRepresentation(MediaType mediaType, OpenAcdAgentRestInfoFull object) {
            super(mediaType, object);
        }

        public OpenAcdAgentRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias(ELEMENT_NAME_AGENT, OpenAcdAgentRestInfoFull.class);
            xstream.alias(ELEMENT_NAME_GROUP, OpenAcdAgentGroupRestInfo.class);
            xstream.alias(ELEMENT_NAME_SKILL, OpenAcdSkillRestInfo.class);
            xstream.alias(ELEMENT_NAME_QUEUE, OpenAcdQueueRestInfo.class);
            xstream.alias(ELEMENT_NAME_CLIENT, OpenAcdClientRestInfo.class);
        }
    }

    // REST info objects
    // -----------------

    static class OpenAcdAgentsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<OpenAcdAgentRestInfoFull> m_agents;

        public OpenAcdAgentsBundleRestInfo(List<OpenAcdAgentRestInfoFull> agents, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_agents = agents;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<OpenAcdAgentRestInfoFull> getAgents() {
            return m_agents;
        }
    }

    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }
}
