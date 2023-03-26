package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
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
import java.util.*;

public class ProcessAgent extends Agent {
    private AID parentVisitor;
    private int dish;
    private int index = 0;
    private List<List<Operation>> operationOrder;
    private int operationsRunning = 0;
    private final List<Integer> completedOperations = new ArrayList<>();
    private LocalDateTime timeStart;

    protected void setup() {
        // Регистрация в DF
        addBehaviour(new RegisterInDFBehaviour(this, "process"));

        // Запись создания агента в логи
        Utils.logAgent(this, "created");

        // Получение параметров
        Object[] args = getArguments();
        parentVisitor = (AID) args[0];
        dish = (int) args[1];

        // Получение операций
        List<Operation> operations = Utils.getDishOperations(dish);

        // Вычисление порядка выполнения операций с учетом параллельности
        operationOrder = Utils.operationsToParallelOperationOrder(operations);

        // Запись времени создания
        timeStart = LocalDateTime.now();

        // Запуск поведения запуска операций
        addBehaviour(new LaunchNextOperationsBehaviour());
    }

    protected void takeDown() {
        // Запись удаления агента в логи
        Utils.logAgent(this, "terminated");

        // Запись процесса в логи
        logProcess();

        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    private class LaunchNextOperationsBehaviour extends OneShotBehaviour {
        public void action() {
            operationsRunning = operationOrder.get(index).size();

            // Отправление запросов супервизору
            AID supervisor = Utils.getAgentsByType(myAgent, "supervisor")[0];
            for (Operation operation : operationOrder.get(index)) {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(supervisor);

                StringBuilder content = new StringBuilder("operation ");
                content.append(Utils.getDishCardByDishId(dish));
                content.append(" ");
                content.append(operation.type());
                content.append(" ");
                content.append(operation.equipment());
                content.append(" ");
                content.append(operation.time());
                for (Pair<Integer, Double> pair : operation.products()) {
                    content.append(" ");
                    content.append(pair.getLeft());
                    content.append(" ");
                    content.append(pair.getRight());
                }

                msg.setContent(content.toString());
                send(msg);
            }

            index++;
            addBehaviour(new MessageReaderBehaviour());
        }
    }

    private class MessageReaderBehaviour extends CyclicBehaviour {
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                if (msg.getPerformative() == ACLMessage.INFORM) {
                    operationsRunning--;

                    completedOperations.add(Utils.getIndexFromName(msg.getSender().getLocalName()));

                    if (operationsRunning == 0) {
                        if (index == operationOrder.size()) {
                            sendMessageDone();
                            doDelete();
                        } else {
                            addBehaviour(new LaunchNextOperationsBehaviour());
                        }
                    }
                }
            } else {
                block();
            }
        }
    }

    private void sendMessageDone() {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(parentVisitor);
        msg.setContent("done");
        send(msg);
    }

    private void logProcess() {
        JSONObject newProcessLog = new JSONObject();

        newProcessLog.put("proc_id", Utils.getIndexFromName(getLocalName()));
        newProcessLog.put("ord_dish", dish);
        newProcessLog.put("proc_started", Utils.converToSimulationTime(timeStart).toString());
        newProcessLog.put("proc_ended", Utils.converToSimulationTime(LocalDateTime.now()).toString());
        newProcessLog.put("proc_active", false);

        JSONArray jsonOperationsArray = new JSONArray();
        for (Integer operationId : completedOperations) {
            JSONObject operation = new JSONObject();
            operation.put("proc_oper", operationId);
            jsonOperationsArray.add(operation);
        }

        newProcessLog.put("proc_operations", jsonOperationsArray);


        JSONObject processesLog;

        synchronized (VisitorAgent.class) {
            try (Reader reader = new FileReader(".\\output\\process_log.txt")) {
                JSONParser parser = new JSONParser();
                processesLog = (JSONObject) parser.parse(reader);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            ((JSONArray) processesLog.get("process_log")).add(newProcessLog);

            try (Writer writer = new FileWriter(".\\output\\process_log.txt")) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                JsonElement je = JsonParser.parseString(processesLog.toString());
                writer.write(gson.toJson(je));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
