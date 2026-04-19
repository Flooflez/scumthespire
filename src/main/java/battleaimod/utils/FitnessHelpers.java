package battleaimod.utils;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class FitnessHelpers {

    // Sum of Poison on all monsters
    public static int PoisonOnAllMonstersPerTurn() {
        int total = 0;
        for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
            if (!m.isDeadOrEscaped()) {
                AbstractPower poison = m.getPower("Poison");
                if (poison != null) {
                    total += poison.amount;
                }
            }
        }
        return total;
    }


    //Sum of Weak on all monsters
    public static int WeakOnAllMonstersPerTurn() {
        int total = 0;
        for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
            if (!m.isDeadOrEscaped()) {
                AbstractPower weak = m.getPower("Weak");
                if (weak != null) {
                    total += weak.amount;
                }
            }
        }
        return total;
    }

    // Sum of Vulnerable on all monsters
    public static int VulnOnAllMonstersPerTurn() {
        int total = 0;
        for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
            if (!m.isDeadOrEscaped()) {
                AbstractPower vuln = m.getPower("Vulnerable");
                if (vuln != null) {
                    total += vuln.amount;
                }
            }
        }
        return total;
    }
}