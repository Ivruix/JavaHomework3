package org.example;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CookAgent extends Agent {
    int id;
    boolean busy = false;

    protected void setup() {
        // Регистрация в DF
        addBehaviour(new RegisterInDFBehaviour(this, "cook"));

        // Запись создания агента в логи
        Utils.logAgent(this, "created");

        // Получение параметров
        Object[] args = getArguments();
        id = (int) args[0];

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

                    if (busy) {
                        reply.setPerformative(ACLMessage.REFUSE);
                    } else {
                        reply.setPerformative(ACLMessage.AGREE);
                        reply.setContent("cook " + id);
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
