package battleaimod.battleai.data.dummycommands;

import com.megacrit.cardcrawl.cards.AbstractCard;
import ludicrousspeed.simulator.commands.Command;
import ludicrousspeed.simulator.commands.HandSelectCommand;

public class DummyHandSelectCommand implements DummyCommand{
    private final AbstractCard card;

    public DummyHandSelectCommand(AbstractCard card) {
        this.card = card;
    }

    @Override
    public Command getRealCommand() {
        int cardIndex = DummyCommand.getCardIndexFromHand(card);

        //TODO: error check for card not found

        return new HandSelectCommand(cardIndex);
    }
}

