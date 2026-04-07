package battleaimod.battleai.data.dummycommands;

import battleaimod.utils.FileLogger;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import ludicrousspeed.simulator.commands.CardCommand;
import ludicrousspeed.simulator.commands.Command;

import java.util.Objects;

public class DummyCardCommand implements DummyCommand {
    private final AbstractCard card;
    private final int enemyIndex;

    public DummyCardCommand(AbstractCard card, int enemyIndex) {
        this.card = card;
        this.enemyIndex = enemyIndex;
    }

    public DummyCardCommand(AbstractCard card) {
        this.card = card;
        this.enemyIndex = -1;
    }

    @Override
    public Command getRealCommand() {
        int cardIndex = DummyCommand.getCardIndexFromHand(card);

        //TODO: error check for card not found
        if(cardIndex == -1){
            FileLogger.logError("card not found: " + card + " id: " + card.getMetricID());
            FileLogger.logError("hand: " + AbstractDungeon.player.hand.group);
            for (AbstractCard c : AbstractDungeon.player.hand.group){
                FileLogger.logError("   card id: " + c.getMetricID());
            }
            return null;
        }

        return new CardCommand(cardIndex, enemyIndex, card.cardID + " " + card.name);
    }
}
