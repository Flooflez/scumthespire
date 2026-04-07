package battleaimod.battleai.data.dummycommands;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import ludicrousspeed.simulator.commands.Command;

public interface DummyCommand {
    public Command getRealCommand();

    public static int getCardIndexFromHand(AbstractCard card){
        int cardIndex = -1;

        for (int i = 0; i < AbstractDungeon.player.hand.group.size(); i++) {
            AbstractCard handCard = AbstractDungeon.player.hand.group.get(i);

            if (handCard.getMetricID().equals(card.getMetricID())) {
                cardIndex = i;
                break;
            }
        }
        return cardIndex; //Returns -1 if not found
    }
}
