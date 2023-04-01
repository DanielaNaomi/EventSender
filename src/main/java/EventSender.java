import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.parsers.*;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.paint.Color;

import javax.swing.Timer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@ExtensionInfo(
        Title = "Event Sender",
        Description = "Send messages to selected friends!",
        Version = "1.3",
        Author = "Thauan"
)

public class EventSender extends ExtensionForm {
    public static EventSender RUNNING_INSTANCE;
    public ListView<String> listFriends;
    public boolean friendsLoaded = false;
    public TreeMap<String, Integer> idList = new TreeMap<>();
    public TreeMap<String, Integer> groupIdList = new TreeMap<>();
    public Button buttonMoveToList;
    public ListView<String> groupListNames;
    public Label labelInfo;
    public Button buttonRemoveFromList;
    public Button buttonSendMessage;
    public TextArea textAreaMessage;
    Timer timerCooldown = new Timer(40000, e -> enableButtonSendMessage());
    @Override
    protected void onStartConnection() {
        System.out.println("Event Sender Started it's connection!");
    }

    @Override
    protected void onShow() {
        timerCooldown.setRepeats(false);
        if(!friendsLoaded) {
            Platform.runLater(() -> {
                labelInfo.setText("Please restart Habbo so the extension fully works. The extension will be disabled now.");
                labelInfo.setTextFill(Color.RED);
                textAreaMessage.setDisable(true);
                listFriends.setDisable(true);
                groupListNames.setDisable(true);
                buttonSendMessage.setDisable(true);
                buttonRemoveFromList.setDisable(true);
                buttonMoveToList.setDisable(true);
            });
        } else {
            Platform.runLater(() -> {
                labelInfo.setText("Friends loaded. Extension is ready.");
                labelInfo.setTextFill(Color.GREEN);
                textAreaMessage.setDisable(false);
                listFriends.setDisable(false);
                groupListNames.setDisable(false);
                buttonSendMessage.setDisable(false);
                buttonRemoveFromList.setDisable(false);
                buttonMoveToList.setDisable(false);
            });
        }
    }

    @Override
    protected void initExtension() {
        RUNNING_INSTANCE = this;

        intercept(HMessage.Direction.TOCLIENT, "FriendListFragment", hMessage -> {
            idList.clear();
            if(listFriends.getItems().size() > 1)
                listFriends.getItems().clear();
            HPacket hPacket = hMessage.getPacket();

            if(!friendsLoaded) {
                for (HFriend user : HFriend.parseFromFragment(hPacket)) {
                    int userId = user.getId();
                    String userName = user.getName();
                    if (user.isOnline()) {
                        if (!idList.containsKey(userName) && !listFriends.getItems().contains(userName)) {
                            idList.put(user.getName(), userId);
                        }

                        sendToClient(new HPacket("FriendListUpdate", HMessage.Direction.TOCLIENT, 0, 1, 0, user.getId(), user.getName(), 1, 0, 0, 0, "", true, false, true, ""));
                    }
                }
                for (Map.Entry<String, Integer> idUsername : idList.entrySet()) {
                    if (!listFriends.getItems().contains(idUsername.getKey()) && !groupListNames.getItems().contains(idUsername.getKey())) {
                        Platform.runLater(() -> listFriends.getItems().add(idUsername.getKey()));
                    }
                }
            }

//            HFriend[] friends = HFriend.parseFromFragment(hPacket); // Thanks to WiredSpast
//            HFriend[] allOffline = Arrays.stream(friends)
//                    .peek(f -> f.setOnline(false))
//                    .toArray(HFriend[]::new);
//            PacketInfo friendListUpdatePacketInfo = getPacketInfoManager().getPacketInfoFromName(HMessage.Direction.TOCLIENT, "");
//            if (friendListUpdatePacketInfo == null) return;
//            sendToClient(HFriend.constructUpdatePacket(allOffline, friendListUpdatePacketInfo.getHeaderId()));

            friendsLoaded = true;
            Platform.runLater(() -> {
                labelInfo.setText("Friends loaded. Extension is ready.");
                labelInfo.setTextFill(Color.GREEN);
            });
        });

        intercept(HMessage.Direction.TOCLIENT, "FriendListUpdate", hMessage -> {
            HPacket hPacket = hMessage.getPacket();

            if(friendsLoaded) {
                for (HFriend user : HFriend.parseFromUpdate(hPacket)) {
                    int userId = user.getId();
                    String userName = user.getName();

                    if (user.isOnline()) {
                        if (!idList.containsKey(userName) && !groupIdList.containsKey(userName)) {
                            idList.put(user.getName(), userId);
                        }
                    } else {
                        if (idList.containsKey(userName) || groupIdList.containsKey(userName)) {
                            Platform.runLater(() -> {
                                idList.remove(userName);
                                groupIdList.remove(userName);
                                listFriends.getItems().remove(userName);
                                groupListNames.getItems().remove(userName);
                            });
                        }
                    }
                }
                for (Map.Entry<String, Integer> idUsername : idList.entrySet()) {
                    if (!listFriends.getItems().contains(idUsername.getKey()) && !groupListNames.getItems().contains(idUsername.getKey())) {
                        Platform.runLater(() -> listFriends.getItems().add(idUsername.getKey()));
                    }
                }
            }

        });

    }
    public void handleButtonSendMessage() {
        Platform.runLater(() -> {
            buttonSendMessage.setDisable(true);
            buttonSendMessage.setText("Sending...");
            labelInfo.setText("Your message is being send to everyone, please wait.");
            labelInfo.setTextFill(Color.BLUE);
        });
        String[] messages = textAreaMessage.getText().split("\n");

        AtomicInteger msgIndex = new AtomicInteger(1);
        AtomicBoolean stop = new AtomicBoolean(false);
        Arrays.stream(messages).forEach(msg -> {
            if(msg.chars().count() > 128) {
                Platform.runLater(() -> labelInfo.setText("The line " + msgIndex + " of the message is to big, the maximum chars allowed are 128."));
                stop.set(true);
            }
            msgIndex.getAndIncrement();
        });
        if(stop.get())
            return;

        new Thread(() -> {
            groupIdList.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .forEach(o -> {
                        if(messages.length == 1) {
                            sendToServer(new HPacket("SendMsg", HMessage.Direction.TOSERVER, o.getValue(), messages[0]));
                            waitAFuckingSecond( 500);
                        }else {
                            Arrays.stream(messages).forEach(msg -> {
                                sendToServer(new HPacket("SendMsg", HMessage.Direction.TOSERVER, o.getValue(), msg));
                                waitAFuckingSecond(1000);
                            });
                        }
                    });

            Platform.runLater(() -> {
                labelInfo.setText("Messages were Sent! Wait the cooldown.");
                labelInfo.setTextFill(Color.GREEN);
                buttonSendMessage.setText("Cooldown...");
            });
            timerCooldown.start();
        }).start();

    }
    public void waitAFuckingSecond(int millisecondActually){
        try {
            Thread.sleep(millisecondActually);
        } catch (InterruptedException ignored) { }
    }

    public void removeFromGroupList(ActionEvent actionEvent) {
        if(groupListNames.getSelectionModel().isEmpty()) {
            Platform.runLater(() -> {
                labelInfo.setText("You need to select at least one friend from Group Message List.");
                labelInfo.setTextFill(Color.RED);
            });
            return;
        }
        ObservableList<String> selectedList = groupListNames.getSelectionModel().getSelectedItems();
        for (String selectedName : selectedList) {
            listFriends.getItems().add(selectedName);
            idList.put(selectedName, groupIdList.get(selectedName));
            groupIdList.remove(selectedName);
            groupListNames.getItems().remove(selectedName);
        }
        Platform.runLater(() -> {
            labelInfo.setText("Friend removed from Group Message List.");
            labelInfo.setTextFill(Color.BLUE);
        });
    }

    public void addToGroupList(ActionEvent actionEvent) {
        if(listFriends.getSelectionModel().isEmpty()) {
            Platform.runLater(() -> {
                labelInfo.setText("Select a player from your online friends.");
                labelInfo.setTextFill(Color.RED);
            });
            return;
        }

        ObservableList<String> selectedList = listFriends.getSelectionModel().getSelectedItems();
        for (String selectedName : selectedList) {
            groupListNames.getItems().add(selectedName);
            groupIdList.put(selectedName, idList.get(selectedName));
            idList.remove(selectedName);
            listFriends.getItems().remove(selectedName);
        }

        Platform.runLater(() -> {
            labelInfo.setText("Friend added to Group Message List.");
            labelInfo.setTextFill(Color.BLUE);
        });

    }
    private void enableButtonSendMessage() {
        Platform.runLater(() -> {
            buttonSendMessage.setDisable(false);
            buttonSendMessage.setText("Send Message");
        });
    }
}
