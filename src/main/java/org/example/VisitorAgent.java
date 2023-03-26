package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
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
import java.util.List;

import static java.lang.Math.min;

public class VisitorAgent extends Agent {
    private String name;
    private LocalDateTime timeStart;
    private LocalDateTime realTimeStart;
    private int total;
    private List<Integer> dishes;
    private int runningProcesses;

    protected void setup() {
        // Регистрация в DF
        addBehaviour(new RegisterInDFBehaviour(this, "visitor"));

        // Запись создания агента в логи
        Utils.logAgent(this, "created");

        // Получение параметров
        Object[] args = getArguments();
        name = (String) args[0];
        timeStart = LocalDateTime.parse((String) args[1]);
        total = (int) args[2];
        dishes = (List<Integer>) args[3];

        // Рассчет задержки
        long timeout = ChronoUnit.MILLIS.between(Utils.getSimulationStart(), timeStart);
        timeout /= Utils.getSimulationSpeed();

        // Рассчет примерного времени выполнения
        double estimation = 0;
        for (int dish : dishes) {
            estimation += Utils.estimateDishPreparationTime(dish);
        }
        System.out.println("Visitor \"" + name + "\" estimated to wait for "
                + String.format("%,.1f", estimation * 60) + " minutes");

        // Добавление поведения заказа
        addBehaviour(new OrderBehaviour(this, timeout));
    }

    protected void takeDown() {
        // Запись удаления агента в логи
        Utils.logAgent(this, "terminated");

        // Запись посещения в логи
        logVisit();

        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    private class OrderBehaviour extends WakerBehaviour {
        public OrderBehaviour(Agent a, long timeout) {
            super(a, timeout);
        }

        protected void onWake() {
            // Сохранение реального времени заказа
            realTimeStart = LocalDateTime.now();

            runningProcesses = dishes.size();

            // Отправление запросов супервизору
            AID supervisor = Utils.getAgentsByType(myAgent, "supervisor")[0];
            for (Integer dish : dishes) {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(supervisor);
                msg.setContent("process " + dish);
                send(msg);
            }

            addBehaviour(new MessageReaderBehaviour());
        }
    }

    private class MessageReaderBehaviour extends CyclicBehaviour {
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                if (msg.getPerformative() == ACLMessage.INFORM) {
                    runningProcesses--;

                    if (runningProcesses == 0) {
                        doDelete();
                    }
                }
            } else {
                block();
            }
        }
    }

    private static int dishId = 1;

    private void logVisit() {
        JSONObject newVisitLog = new JSONObject();
        newVisitLog.put("vis_name", name);
        newVisitLog.put("vis_ord_started", timeStart.toString());
        newVisitLog.put("vis_ord_ended", Utils.converToSimulationTime(LocalDateTime.now()).toString());
        newVisitLog.put("vis_ord_total", total);

        JSONArray jsonDishesArray = new JSONArray();

        synchronized (VisitorAgent.class) {
            for (Integer dishType : dishes) {
                JSONObject dish = new JSONObject();
                dish.put("ord_dish_id", dishId);
                dish.put("menu_dish", dishType);

                dishId++;

                jsonDishesArray.add(dish);
            }
        }

        newVisitLog.put("vis_ord_dishes", jsonDishesArray);

        JSONObject visitorsLog;

        synchronized (VisitorAgent.class) {
            try (Reader reader = new FileReader(".\\output\\visitors_orders.txt")) {
                JSONParser parser = new JSONParser();
                visitorsLog = (JSONObject) parser.parse(reader);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            ((JSONArray) visitorsLog.get("visitors_orders")).add(newVisitLog);

            try (Writer writer = new FileWriter(".\\output\\visitors_orders.txt")) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                JsonElement je = JsonParser.parseString(visitorsLog.toString());
                writer.write(gson.toJson(je));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
