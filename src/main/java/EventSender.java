import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.parsers.HFriend;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.paint.Color;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ExtensionInfo(Title = "Event Sender", Description = "Send messages to selected friends!", Version = "1.4", Author = "Thauan")

public class EventSender extends ExtensionForm {
    public static EventSender RUNNING_INSTANCE;

    protected List<Friend> onlineFriendsList = new ArrayList<>();

    // UI elements
    public ListView<Friend> onlineFriendsListView;
    public ListView<Friend> sendFriendsListView;
    public Label statusLabel;
    public Button removeFromSendListButton;
    public Button sendMessageButton;
    public TextArea messageTextArea;
    public Button addToSendListButton;

    Timer timerCooldown = new Timer(40000, e -> setGuiState(GuiState.READY));

    @Override
    protected void initExtension() {
        RUNNING_INSTANCE = this;

        timerCooldown.setRepeats(false);
        intercept(HMessage.Direction.TOCLIENT, "FriendListFragment", this::onFriendListFragment);
        intercept(HMessage.Direction.TOCLIENT, "FriendListUpdate", this::onFriendListUpdate);
    }

    @Override
    protected void onStartConnection() {
        System.out.println("Refreshing friends list, new habbo connection is made!");
        setGuiState(GuiState.INITIALIZING);
        onlineFriendsList.clear();
    }

    @Override
    protected void onEndConnection() {
        System.out.println("Refreshing friends list, habbo disconnected");
        setGuiState(GuiState.INITIALIZING);
        onlineFriendsList.clear();
    }

    protected void setGuiState(GuiState guiState) {
        Platform.runLater(() -> {
            // Disabling of UI elements
            switch (guiState) {
                case INITIALIZING:
                    messageTextArea.setDisable(true);
                    onlineFriendsListView.setDisable(true);
                    sendFriendsListView.setDisable(true);
                    sendMessageButton.setDisable(true);
                    removeFromSendListButton.setDisable(true);
                    addToSendListButton.setDisable(true);
                    break;
                case READY:
                case SENDING:
                case COOLDOWN:
                    messageTextArea.setDisable(false);
                    onlineFriendsListView.setDisable(false);
                    sendFriendsListView.setDisable(false);
                    sendMessageButton.setDisable(false);
                    removeFromSendListButton.setDisable(false);
                    addToSendListButton.setDisable(false);
                    break;
            }

            // Setting the text
            switch (guiState) {
                case INITIALIZING:
                    setStatusLabel("(Re)start Habbo to load your friend list.", Color.RED);
                    break;
                case READY:
                    setStatusLabel("Ready to send!", Color.GREEN);
                    break;
                case SENDING:
                    setStatusLabel("Sending your message, please wait..", Color.BLUE);
                    break;
                case COOLDOWN:
                    setStatusLabel("Messages sent! Cooldown period active, please wait before spamming your friends again..", Color.ORANGE);
                    break;
            }
        });
    }

    protected void setStatusLabel(String text, Color color) {
        Platform.runLater(() -> {
            statusLabel.setText(text);
            statusLabel.setTextFill(color);
        });
    }

    protected void addOnlineFriend(HFriend hFriend) {
        // Friend exists?
        if (onlineFriendsList.stream().anyMatch(friend -> friend.getId() == hFriend.getId())) {
            return;
        }

        Friend friend = new Friend(hFriend.getId(), hFriend.getName());
        onlineFriendsList.add(friend);
        onlineFriendsListView.getItems().add(friend);
    }
    
    protected Friend getFriendById(int id) {
        return onlineFriendsList.stream().filter(friend -> friend.getId() == id).findFirst().orElse(null);
    }

    protected void removeOnlineFriend(HFriend hFriend) {
        // Friend exists?
        Friend friend = getFriendById(hFriend.getId());
        if (friend == null) {
            return;
        }
        
        onlineFriendsList.remove(friend);
        onlineFriendsListView.getItems().remove(friend);
        sendFriendsListView.getItems().remove(friend);
    }

    protected void onFriendListFragment(HMessage hMessage) {
        HPacket hPacket = hMessage.getPacket();

        for (HFriend user : HFriend.parseFromFragment(hPacket)) {
            if (user.isOnline()) {
                addOnlineFriend(user);
            }
        }

        setGuiState(GuiState.READY);
    }

    protected void onFriendListUpdate(HMessage hMessage) {
        HPacket hPacket = hMessage.getPacket();

        for (HFriend hFriend : HFriend.parseFromUpdate(hPacket)) {
            if (hFriend.isOnline()) {
                addOnlineFriend(hFriend);
            } else {
                removeOnlineFriend(hFriend);
            }
        }
    }

    public void handleButtonSendMessage() {
        setGuiState(GuiState.SENDING);
        String[] messages = messageTextArea.getText().split("\n");

        int tooLongMessageIndex = -1;
        for (int i = 0; i < messages.length; i++) {
            if (messages[i].length() > 128) {
                tooLongMessageIndex = i;
                break;
            }
        }
        if(tooLongMessageIndex != -1) {
            setGuiState(GuiState.READY);
            setStatusLabel("The message on line " + (tooLongMessageIndex + 1) + " is too long! (max 128 characters)", Color.RED);
            return;
        }

        new Thread(() -> {
            sendFriendsListView.getItems().forEach(friend -> {
                // Friend still online?
                if(onlineFriendsList.contains(friend)) {
                    // for each message in messages
                    Arrays.stream(messages).forEach(message -> {
                        sendToServer(new HPacket("SendMsg", HMessage.Direction.TOSERVER, friend.getId(), message));
                        waitAnActualFuckingMinute(500);
                    });
                }
            });

            setGuiState(GuiState.COOLDOWN);
            timerCooldown.start();
        }).start();

    }

    public void waitAnActualFuckingMinute(int jkItsMilliseconds) {
        try {
            Thread.sleep(jkItsMilliseconds);
        } catch (InterruptedException ignored) {
        }
    }

    public void removeFromGroupList(ActionEvent actionEvent) {
        if (sendFriendsListView.getSelectionModel().isEmpty()) {
            return;
        }

        for(Friend friend : sendFriendsListView.getSelectionModel().getSelectedItems()) {
            onlineFriendsListView.getItems().add(friend);
            sendFriendsListView.getItems().remove(friend);
        }
    }

    public void addToGroupList(ActionEvent actionEvent) {
        if (onlineFriendsListView.getSelectionModel().isEmpty()) {
            return;
        }

        for(Friend friend : onlineFriendsListView.getSelectionModel().getSelectedItems()) {
            onlineFriendsListView.getItems().remove(friend);
            sendFriendsListView.getItems().add(friend);
        }
    }
}
