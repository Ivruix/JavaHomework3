package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class OperationAgent extends Agent {
    private AID parentProcess;
    private int type;
    private int equipment;
    private double time;
    private List<Pair<Integer, Double>> products;
    private LocalDateTime timeStart;
    private List<Pair<AID, Integer>> bookedCooks = new ArrayList<>();
    private List<Pair<AID, Integer>> bookedEquipment = new ArrayList<>();
    private int dishCard;


    protected void setup() {
        // Регистрация в DF
        addBehaviour(new RegisterInDFBehaviour(this, "operation"));

        // Запись создания агента в логи
        Utils.logAgent(this, "created");

        // Получение параметров
        Object[] args = getArguments();
        parentProcess = (AID) args[0];
        dishCard = (int) args[1];
        type = (int) args[2];
        equipment = (int) args[3];
        time = (double) args[4];
        products = (List<Pair<Integer, Double>>) args[5];

        // Запись времени создания
        timeStart = LocalDateTime.now();

        // Запуск поведения поиска свободных ресурсов
        addBehaviour(new FindResourcesBehaviour());
    }

    protected void takeDown() {
        // Запись удаления агента в логи
        Utils.logAgent(this, "terminated");

        // Запись операции в логи
        logOperation();

        Utils.logAgent(this, "terminated");
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    private void sendMessageDone() {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(parentProcess);
        msg.setContent("done");
        send(msg);
    }

    private void sendInform(AID aid) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(aid);
        send(msg);
    }

    private class DoOperationBehaviour extends Behaviour {
        int stage = 0;
        boolean done = false;
        int responsesWaiting = 0;

        public void action() {
            switch (stage) {
                case 0:
                    AID storage = Utils.getAgentsByType(myAgent, "storage")[0];
                    for (Pair<Integer, Double> product : products) {
                        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                        msg.addReceiver(storage);
                        msg.setContent(product.getLeft() + " " + product.getRight());
                        send(msg);
                    }
                    responsesWaiting = products.size();
                    stage = 1;
                    break;
                case 1:
                    ACLMessage msg = myAgent.receive();
                    if (msg != null) {
                        if (msg.getPerformative() == ACLMessage.AGREE) {
                            responsesWaiting--;
                        } else if (msg.getPerformative() == ACLMessage.REFUSE) {
                            sendInform(bookedCooks.get(0).getLeft());
                            sendInform(bookedEquipment.get(0).getLeft());

                            sendMessageDone();

                            throw new RuntimeException("Not enough resources in storage!");
                        }
                    } else {
                        block();
                    }

                    if (responsesWaiting == 0) {
                        stage = 2;
                    }
                    break;
                case 2:
                    try {
                        TimeUnit.MILLISECONDS.sleep((long) ((time * 60 * 60 * 1000) / Utils.getSimulationSpeed()));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    sendInform(bookedCooks.get(0).getLeft());
                    sendInform(bookedEquipment.get(0).getLeft());

                    done = true;

                    sendMessageDone();
                    doDelete();
                    break;
                default:
                    break;
            }
        }

        public boolean done() {
            return done;
        }
    }

    private class FindResourcesBehaviour extends Behaviour {
        int stage = 0;
        int totalSent = 0;
        boolean done = false;

        public void action() {
            switch (stage) {
                case 0:
                    // Отправление запросов поварам
                    AID[] cooksArray = Utils.getAgentsByType(myAgent, "cook");
                    ACLMessage msgForCooks = new ACLMessage(ACLMessage.REQUEST);
                    for (AID receiver : cooksArray) {
                        msgForCooks.addReceiver(receiver);
                    }
                    send(msgForCooks);

                    // Отправление запросов технике
                    AID[] equipmentArray = Utils.getAgentsByType(myAgent, "equipment");
                    ACLMessage msgForEquipment = new ACLMessage(ACLMessage.REQUEST);
                    msgForEquipment.setContent(Integer.toString(equipment));
                    for (AID receiver : equipmentArray) {
                        msgForEquipment.addReceiver(receiver);
                    }
                    send(msgForEquipment);

                    totalSent = cooksArray.length + equipmentArray.length;
                    stage = 1;
                    break;
                case 1:
                    ACLMessage msg = myAgent.receive();
                    if (msg != null) {
                        if (msg.getPerformative() == ACLMessage.AGREE) {
                            String[] tokens = msg.getContent().split(" ");

                            if (Objects.equals(tokens[0], "cook")) {
                                bookedCooks.add(new Pair<>(msg.getSender(), Integer.parseInt(tokens[1])));
                            } else if (Objects.equals(tokens[0], "equipment")) {
                                bookedEquipment.add(new Pair<>(msg.getSender(), Integer.parseInt(tokens[1])));
                            }

                            totalSent--;
                        } else if (msg.getPerformative() == ACLMessage.REFUSE) {
                            totalSent--;
                        }
                    } else {
                        block();
                    }

                    if (totalSent == 0) {
                        stage = 2;
                    }

                    break;
                case 2:
                    if (bookedCooks.size() == 0) {
                        for (Pair<AID, Integer> cook : bookedEquipment) {
                            sendInform(cook.getLeft());
                        }

                        bookedEquipment.clear();
                        stage = 0;
                        break;
                    }

                    if (bookedEquipment.size() == 0) {
                        for (Pair<AID, Integer> cook : bookedCooks) {
                            sendInform(cook.getLeft());
                        }

                        bookedCooks.clear();
                        stage = 0;
                        break;
                    }

                    for (int i = 1; i < bookedCooks.size(); i++) {
                        sendInform(bookedCooks.get(i).getLeft());
                    }

                    for (int i = 1; i < bookedEquipment.size(); i++) {
                        sendInform(bookedEquipment.get(i).getLeft());
                    }

                    addBehaviour(new DoOperationBehaviour());

                    done = true;
                    break;
                default:
                    break;
            }
        }

        public boolean done() {
            return done;
        }
    }




    private void logOperation() {
        JSONObject newOperationLog = new JSONObject();

        newOperationLog.put("oper_id", Utils.getIndexFromName(getLocalName()));
        newOperationLog.put("oper_proc", Utils.getIndexFromName(parentProcess.getLocalName()));
        newOperationLog.put("oper_card", dishCard);
        newOperationLog.put("oper_started", Utils.converToSimulationTime(timeStart).toString());
        newOperationLog.put("oper_ended", Utils.converToSimulationTime(LocalDateTime.now()).toString());
        newOperationLog.put("oper_equip_id", bookedEquipment.get(0).getRight());
        newOperationLog.put("oper_coocker_id", bookedCooks.get(0).getRight());
        newOperationLog.put("oper_active", false);

        JSONObject operationsLog;

        synchronized (OperationAgent.class) {
            try (Reader reader = new FileReader(".\\output\\operation_log.txt")) {
                JSONParser parser = new JSONParser();
                operationsLog = (JSONObject) parser.parse(reader);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            ((JSONArray) operationsLog.get("operation_log")).add(newOperationLog);

            try (Writer writer = new FileWriter(".\\output\\operation_log.txt")) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                JsonElement je = JsonParser.parseString(operationsLog.toString());
                writer.write(gson.toJson(je));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
