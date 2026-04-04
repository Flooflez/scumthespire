package battleaimod.battleai.data.dummycommands;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import ludicrousspeed.simulator.commands.CardCommand;
import ludicrousspeed.simulator.commands.Command;

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
        int cardIndex = -1;

        for (int i = 0; i < AbstractDungeon.player.hand.group.size(); i++) {
            AbstractCard handCard = AbstractDungeon.player.hand.group.get(i);

            if (handCard.getMetricID().equals(card.getMetricID())) {
                cardIndex = i;
                break;
            }
        }


        Command cmd = new CardCommand(cardIndex, enemyIndex, card.cardID + " " + card.name);
        return cmd;
    }
}
