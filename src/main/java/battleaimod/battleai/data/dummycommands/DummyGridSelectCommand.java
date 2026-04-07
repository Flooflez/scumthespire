package battleaimod.battleai.data.dummycommands;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import ludicrousspeed.simulator.commands.Command;
import ludicrousspeed.simulator.commands.GridSelectCommand;

public class DummyGridSelectCommand implements DummyCommand{
    private final AbstractCard card;

    public DummyGridSelectCommand(AbstractCard card) {
        this.card = card;
    }

    @Override
    public Command getRealCommand() {
        int cardIndex = -1;
        for(int i = 0; i < AbstractDungeon.gridSelectScreen.targetGroup.size(); ++i) {
            AbstractCard card = AbstractDungeon.gridSelectScreen.targetGroup.group.get(i);
            if (this.card.getMetricID().equals(card.getMetricID())) {
                cardIndex = i;
                break;
            }
        }

        //TODO: error check card not found

        return new GridSelectCommand(cardIndex);
    }
}
