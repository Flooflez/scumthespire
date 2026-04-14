package battleaimod.networking;

import basemod.interfaces.PostUpdateSubscriber;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import battleaimod.BattleAiMod;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

import static battleaimod.networking.BattleClientController.startServerThread;

public class AutoPlayController implements PostUpdateSubscriber {

    private boolean hasTriggeredThisTurn = false;


    //original is private, not sure if I can make it public, so I create a copy.
    private boolean canSendStateSafe() {
        boolean controllerRunning = BattleAiMod.rerunController != null && !BattleAiMod.rerunController.isDone;
        return BattleClientController.readyForUpdate() && !controllerRunning;
    }

    private boolean inCombat() {
        return CardCrawlGame.isInARun() && AbstractDungeon.currMapNode != null && AbstractDungeon
                .getCurrRoom() != null && AbstractDungeon
                .getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT;
    }

    @Override
    public void receivePostUpdate() {

        if (!inCombat()) return;

        // Trigger once per player turn when game is ready.
        if (!AbstractDungeon.actionManager.turnHasEnded
                && AbstractDungeon.actionManager.isEmpty()
                && !hasTriggeredThisTurn
                && BattleClientController.readyForUpdate()) {

            triggerSimulation();
            hasTriggeredThisTurn = true;
        }

        // Reset at turn end
        if (AbstractDungeon.actionManager.turnHasEnded) {
            hasTriggeredThisTurn = false;
        }
    }

    private void triggerSimulation() {
        if (!canSendStateSafe()) {
            return;
        }
        try {
            if (BattleAiMod.aiClient == null) {
                BattleAiMod.aiClient = new AiClient();
            }

            if (BattleAiMod.aiClient != null) {
                BattleAiMod.aiClient.sendState();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
