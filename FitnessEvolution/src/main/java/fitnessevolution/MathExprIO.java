package fitnessevolution;

import io.jenetics.ext.util.Tree;
import io.jenetics.prog.op.MathExpr;
import io.jenetics.prog.op.Op;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MathExprIO {
    private MathExprIO() {}

    public static Tree<Op<Double>, ?> parseTemplate(Path path) throws IOException {
        return MathExpr.parse(Files.readString(path).trim()).tree();
    }

    public static String serialize(Tree<? extends Op<Double>, ?> tree) {
        return new MathExpr(tree).toString();
    }
}
