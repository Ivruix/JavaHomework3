package org.example;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

public class EquipmentAgent extends Agent {
    int type;
    int id;
    boolean busy = false;

    protected void setup() {
        // Регистрация в DF
        addBehaviour(new RegisterInDFBehaviour(this, "equipment"));

        // Запись создания агента в логи
        Utils.logAgent(this, "created");

        // Получение параметров
        Object[] args = getArguments();
        type = (int) args[0];
        id = (int) args[1];

        // Запуск поведения получения сообщений
        addBehaviour(new MessageReaderBehaviour());
    }

    protected void takeDown() {
        // Запись удаления агента в логи
        Utils.logAgent(this, "terminated");

        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    private class MessageReaderBehaviour extends CyclicBehaviour {
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                if (msg.getPerformative() == ACLMessage.REQUEST) {
                    ACLMessage reply = msg.createReply();

                    int requestedType = Integer.parseInt(msg.getContent());

                    if (busy || (requestedType != type)) {
                        reply.setPerformative(ACLMessage.REFUSE);
                    } else {
                        reply.setPerformative(ACLMessage.AGREE);
                        reply.setContent("equipment " + id);
                        busy = true;
                    }

                    send(reply);
                } else if (msg.getPerformative() == ACLMessage.INFORM) {
                    busy = false;
                }
            } else {
                block();
            }
        }
    }
}