package battleaimod.patches;

import basemod.ReflectionHacks;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DiscardAction;
import com.megacrit.cardcrawl.actions.common.DiscardSpecificCardAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.EmptyDeckShuffleAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.core.Settings;

public class FastActionPatches {

    private static final float WAIT_ACTION_MAX_DURATION = 0.001f;
    private static final float VFX_ACTION_MAX_DURATION = 0.002f;
    private static final float DRAW_ACTION_MAX_DURATION = 0.001f;
    private static final float SHUFFLE_ACTION_MAX_DURATION = 0.001f;
    private static final float DISCARD_ACTION_MAX_DURATION = 0.001f;

    public static void enableFastMode() {
        Settings.FAST_MODE = true;
    }

    public static void disableFastMode() {
        //Settings.FAST_MODE = false;
    }

    private static void clampActionDuration(AbstractGameAction action, float maxDuration) {
        if (action == null) return;

        try {
            float duration = ReflectionHacks.getPrivate(
                    action,
                    AbstractGameAction.class,
                    "duration"
            );

            float startDuration = ReflectionHacks.getPrivate(
                    action,
                    AbstractGameAction.class,
                    "startDuration"
            );

            if (duration > maxDuration) {
                ReflectionHacks.setPrivate(
                        action,
                        AbstractGameAction.class,
                        "duration",
                        maxDuration
                );
            }

            if (startDuration > maxDuration) {
                ReflectionHacks.setPrivate(
                        action,
                        AbstractGameAction.class,
                        "startDuration",
                        maxDuration
                );
            }
        } catch (Exception ignored) {
        }
    }

    @SpirePatch(
            clz = WaitAction.class,
            method = SpirePatch.CONSTRUCTOR,
            paramtypez = { float.class }
    )
    public static class FastWaitActionPatch {
        public static void Postfix(WaitAction __instance) {
            clampActionDuration(__instance, WAIT_ACTION_MAX_DURATION);
        }
    }

    @SpirePatch(
            clz = VFXAction.class,
            method = SpirePatch.CONSTRUCTOR,
            paramtypez = {
                    com.megacrit.cardcrawl.vfx.AbstractGameEffect.class,
                    float.class
            }
    )
    public static class FastVFXActionPatch1 {
        public static void Postfix(VFXAction __instance) {
            clampActionDuration(__instance, VFX_ACTION_MAX_DURATION);
        }
    }

    @SpirePatch(
            clz = VFXAction.class,
            method = SpirePatch.CONSTRUCTOR,
            paramtypez = {
                    com.megacrit.cardcrawl.core.AbstractCreature.class,
                    com.megacrit.cardcrawl.vfx.AbstractGameEffect.class,
                    float.class
            }
    )
    public static class FastVFXActionPatch2 {
        public static void Postfix(VFXAction __instance) {
            clampActionDuration(__instance, VFX_ACTION_MAX_DURATION);
        }
    }

    @SpirePatch(
            clz = DrawCardAction.class,
            method = SpirePatch.CONSTRUCTOR,
            paramtypez = { int.class }
    )
    public static class FastDrawCardActionPatch1 {
        public static void Postfix(DrawCardAction __instance) {
            clampActionDuration(__instance, DRAW_ACTION_MAX_DURATION);
        }
    }

    @SpirePatch(
            clz = DrawCardAction.class,
            method = SpirePatch.CONSTRUCTOR,
            paramtypez = {
                    com.megacrit.cardcrawl.core.AbstractCreature.class,
                    int.class
            }
    )
    public static class FastDrawCardActionPatch2 {
        public static void Postfix(DrawCardAction __instance) {
            clampActionDuration(__instance, DRAW_ACTION_MAX_DURATION);
        }
    }

    @SpirePatch(
            clz = DrawCardAction.class,
            method = SpirePatch.CONSTRUCTOR,
            paramtypez = {
                    com.megacrit.cardcrawl.core.AbstractCreature.class,
                    int.class,
                    boolean.class
            }
    )
    public static class FastDrawCardActionPatch3 {
        public static void Postfix(DrawCardAction __instance) {
            clampActionDuration(__instance, DRAW_ACTION_MAX_DURATION);
        }
    }

    @SpirePatch(
            clz = EmptyDeckShuffleAction.class,
            method = SpirePatch.CONSTRUCTOR
    )
    public static class FastEmptyDeckShuffleActionPatch {
        public static void Postfix(EmptyDeckShuffleAction __instance) {
            clampActionDuration(__instance, SHUFFLE_ACTION_MAX_DURATION);
        }
    }

    @SpirePatch(
            clz = DiscardAction.class,
            method = SpirePatch.CONSTRUCTOR,
            paramtypez = {
                    com.megacrit.cardcrawl.core.AbstractCreature.class,
                    com.megacrit.cardcrawl.core.AbstractCreature.class,
                    int.class,
                    boolean.class
            }
    )
    public static class FastDiscardActionPatch {
        public static void Postfix(DiscardAction __instance) {
            clampActionDuration(__instance, DISCARD_ACTION_MAX_DURATION);
        }
    }

    @SpirePatch(
            clz = DiscardSpecificCardAction.class,
            method = SpirePatch.CONSTRUCTOR,
            paramtypez = {
                    com.megacrit.cardcrawl.cards.AbstractCard.class
            }
    )
    public static class FastDiscardSpecificCardActionPatch1 {
        public static void Postfix(DiscardSpecificCardAction __instance) {
            clampActionDuration(__instance, DISCARD_ACTION_MAX_DURATION);
        }
    }

    @SpirePatch(
            clz = DiscardSpecificCardAction.class,
            method = SpirePatch.CONSTRUCTOR,
            paramtypez = {
                    com.megacrit.cardcrawl.cards.AbstractCard.class,
                    com.megacrit.cardcrawl.cards.CardGroup.class
            }
    )
    public static class FastDiscardSpecificCardActionPatch2 {
        public static void Postfix(DiscardSpecificCardAction __instance) {
            clampActionDuration(__instance, DISCARD_ACTION_MAX_DURATION);
        }
    }
}