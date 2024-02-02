import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.parsers.HFriend;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@ExtensionInfo(Title = "Event Sender", Description = "Send messages to selected friends!", Version = "1.5", Author = "Thauan")

public class EventSender extends ExtensionForm {
    public static EventSender RUNNING_INSTANCE;

    protected List<Friend> onlineFriendsList = new ArrayList<>();
    protected boolean ignoreFriendListFragment = false;

    // UI elements
    public ListView<Friend> onlineFriendsListView;
    public ListView<Friend> sendFriendsListView;
    public Label statusLabel;
    public Button removeFromSendListButton;
    public Button sendMessageButton;
    public Button addToSendListButton;
    public Button addAllToSendListButton;
    public Button removeAllFromSendListButton;
    public TextArea messageTextArea;
    public ProgressBar sendingProgressBar;
    public Label sendingProgressBarLabel;


    Timer timerCooldown = new Timer(40000, e -> setGuiState(GuiState.READY));
    Timer timerResetIgnoreFriendListFragment = new Timer(5000, e -> ignoreFriendListFragment = false);

    @Override
    protected void initExtension() {
        RUNNING_INSTANCE = this;

        timerCooldown.setRepeats(false);
        timerResetIgnoreFriendListFragment.setRepeats(false);

        intercept(HMessage.Direction.TOCLIENT, "FriendListFragment", this::onFriendListFragment);
        intercept(HMessage.Direction.TOCLIENT, "FriendListUpdate", this::onFriendListUpdate);
        clearFriends();
    }

    @Override
    protected void onStartConnection() {
        System.out.println("Refreshing friends list, new habbo connection is made!");
        clearFriends();
    }

    @Override
    protected void onEndConnection() {
        System.out.println("Refreshing friends list, habbo disconnected");
        clearFriends();
    }

    protected void clearFriends() {
        setGuiState(GuiState.INITIALIZING);
        onlineFriendsList.clear();
        Platform.runLater(() -> {
            onlineFriendsListView.getItems().clear();
            sendFriendsListView.getItems().clear();
        });
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
                    addAllToSendListButton.setDisable(true);
                    removeAllFromSendListButton.setDisable(true);
                    break;
                case COOLDOWN:
                case SENDING:
                case READY:
                    messageTextArea.setDisable(false);
                    onlineFriendsListView.setDisable(false);
                    sendFriendsListView.setDisable(false);
                    sendMessageButton.setDisable(false);
                    removeFromSendListButton.setDisable(false);
                    addToSendListButton.setDisable(false);
                    addAllToSendListButton.setDisable(false);
                    removeAllFromSendListButton.setDisable(false);
                    break;
            }

            // Progressbar
            switch (guiState) {
                case COOLDOWN:
                case READY:
                case INITIALIZING:
                    sendingProgressBar.setVisible(false);
                    sendingProgressBar.setProgress(0.0);
                    sendingProgressBarLabel.setVisible(false);
                    sendingProgressBarLabel.setText("0 / 0");
                    break;
                case SENDING:
                    sendingProgressBar.setVisible(true);
                    sendingProgressBarLabel.setVisible(true);
                    break;
            }

            // Setting the text
            switch (guiState) {
                case INITIALIZING:
                    setStatusLabel("Click \"Reload Friend List\" to load your friend list.", Color.RED);
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
        Platform.runLater(() -> onlineFriendsListView.getItems().add(friend));
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
        Platform.runLater(() -> {
            onlineFriendsListView.getItems().remove(friend);
            sendFriendsListView.getItems().remove(friend);
        });
    }

    protected void onFriendListFragment(HMessage hMessage) {
        if(ignoreFriendListFragment) {
            hMessage.setBlocked(true);
        }

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

    public void onReloadFriendListButtonClick() {
        clearFriends();
        ignoreFriendListFragment = true;
        sendToServer(new HPacket("MessengerInit", HMessage.Direction.TOSERVER));
        timerResetIgnoreFriendListFragment.start();
    }

    public void onSendMessageButtonClick() {
        setGuiState(GuiState.SENDING);
        String[] messages = messageTextArea.getText().split("\n");

        int tooLongMessageIndex = -1;
        for (int i = 0; i < messages.length; i++) {
            if (messages[i].length() > 128) {
                tooLongMessageIndex = i;
                break;
            }
        }
        if (tooLongMessageIndex != -1) {
            setGuiState(GuiState.READY);
            setStatusLabel("The message on line " + (tooLongMessageIndex + 1) + " is too long! (max 128 characters)", Color.RED);
            return;
        }

        new Thread(() -> {
            final int totalMessages = messages.length * sendFriendsListView.getItems().size();
            AtomicInteger messagesSent = new AtomicInteger();
            messagesSent.set(0);
            Platform.runLater(() -> {
                sendingProgressBar.setProgress(0.0);
                sendingProgressBarLabel.setText(messagesSent + " / " + totalMessages);
            });

            sendFriendsListView.getItems().forEach(friend -> {
                // Friend still online?
                if (onlineFriendsList.contains(friend)) {
                    // for each message in messages
                    Arrays.stream(messages).forEach(message -> {
                        sendToServer(new HPacket("SendMsg", HMessage.Direction.TOSERVER, friend.getId(), message));
                        messagesSent.addAndGet(1);
                        Platform.runLater(() -> {
                            sendingProgressBar.setProgress((double) messagesSent.get() / totalMessages);
                            sendingProgressBarLabel.setText(messagesSent + " / " + totalMessages);
                        });
                        waitAnActualFuckingMinute(500);
                    });
                } else {
                    messagesSent.addAndGet(messages.length);
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

    public void onAddToSendListButtonClick() {
        for (Friend friend : onlineFriendsListView.getSelectionModel().getSelectedItems()) {
            Platform.runLater(() -> {
                onlineFriendsListView.getItems().remove(friend);
                sendFriendsListView.getItems().add(friend);
            });
        }
    }

    public void onAddAllToSendListButtonClick() {
        for (Friend friend : onlineFriendsListView.getItems()) {
            Platform.runLater(() -> {
                onlineFriendsListView.getItems().remove(friend);
                sendFriendsListView.getItems().add(friend);
            });
        }
    }

    public void onRemoveFromSendListButtonClick() {
        for (Friend friend : sendFriendsListView.getSelectionModel().getSelectedItems()) {
            Platform.runLater(() -> {
                onlineFriendsListView.getItems().add(friend);
                sendFriendsListView.getItems().remove(friend);
            });
        }
    }

    public void onRemoveAllFromSendListButtonClick() {
        for (Friend friend : sendFriendsListView.getItems()) {
            Platform.runLater(() -> {
                onlineFriendsListView.getItems().add(friend);
                sendFriendsListView.getItems().remove(friend);
            });
        }
    }
}
