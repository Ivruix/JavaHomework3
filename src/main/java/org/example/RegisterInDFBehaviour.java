package org.example;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

// Класс поведения записи в DF
public class RegisterInDFBehaviour extends OneShotBehaviour {
    private final String serviceType;

    public RegisterInDFBehaviour(Agent agent, String serviceType) {
        super(agent);
        this.serviceType = serviceType;
    }

    @Override
    public void action() {
        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType);
        sd.setName(myAgent.getName());
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(myAgent.getAID());
        dfd.addServices(sd);

        try {
            DFAgentDescription[] agents = DFService.search(myAgent, dfd);
            if (agents.length > 0) {
                DFService.deregister(myAgent, dfd);
            }
            DFService.register(myAgent, dfd);
        } catch (Exception ex) {
            myAgent.doDelete();
        }
    }
}
