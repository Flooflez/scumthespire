package battleaimod.battleai.data.dummycommands;

import ludicrousspeed.simulator.commands.Command;
import ludicrousspeed.simulator.commands.EndCommand;

public class DummyEndCommand implements DummyCommand {
    @Override
    public Command getRealCommand() {
        Command cmd = new EndCommand();
        cmd.execute();
        return cmd;
    }
}
