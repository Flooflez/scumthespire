package battleaimod.battleai.data.dummycommands;

import com.megacrit.cardcrawl.cards.AbstractCard;
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
        int cardIndex = DummyCommand.getCardIndexFromHand(card);

        //TODO: error check for card not found

        return new CardCommand(cardIndex, enemyIndex, card.cardID + " " + card.name);
    }
}
