package battleaimod.battleai.data;

import battleaimod.battleai.data.dummycommands.DummyCardCommand;
import battleaimod.battleai.data.dummycommands.DummyCommand;
import com.megacrit.cardcrawl.cards.AbstractCard;

import java.util.ArrayList;
import java.util.List;

public class CardAction {
    /*
    A Single CardAction class can represent multiple commands
    E.g. play card into discard = 2 commands
    But since these 2 commands must be played consecutively, they must be evolved together as a single unit
     */

    private AbstractCard mainCard;
    private final int enemyIndex;



    public CardAction(AbstractCard mainCard, int enemyIndex) {
        this.mainCard = mainCard;
        this.enemyIndex = enemyIndex;
    }

    public CardAction(AbstractCard mainCard) {
        this.mainCard = mainCard;
        this.enemyIndex = -1;
    }

    public List<DummyCommand> getDummyCommands(){
        List<DummyCommand> cmds = new ArrayList<>();

        cmds.add(new DummyCardCommand(mainCard, enemyIndex));
        //TODO: Add HandSelectCommand + GridSelectCommand if needed

        return cmds;
    }
}
