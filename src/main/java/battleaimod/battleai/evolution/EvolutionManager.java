package battleaimod.battleai.evolution;

import basemod.interfaces.PostUpdateSubscriber;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

public class EvolutionManager implements PostUpdateSubscriber {

    @Override
    public void receivePostUpdate() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) {

        }
    }

    private void getInitialPopulation(){

    }
}
