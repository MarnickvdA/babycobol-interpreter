package nl.utwente.interpreter;

import nl.utwente.interpreter.model.*;
import nl.utwente.interpreter.exception.GotoException;
import nl.utwente.interpreter.exception.InterpreterException;
import nl.utwente.interpreter.exception.NextSentenceException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.script.*;

public class Visitor extends BabyCobolBaseVisitor<Object> {
    private Boolean testMode = false;
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, BabyCobolParser.ParagraphContext> paragraphs = new LinkedHashMap<>();
    private final Map<BabyCobolParser.GotoStatementContext, String> gotoLabelsMap = new HashMap<>();
    private final Scanner sc = new Scanner(System.in);
    private Object evaluateObject = null;
    private Loop currentLoop;
    private Tree root;
    private final List<Tree> dataStructures = new ArrayList<>();
    private ProgramOutput testOutput = null;
    private String signalParagraph = null;
    private String gotoLabel;
    private String previousContractedComparisonOp = null;

    public Visitor() {
    }

    public Visitor(ProgramOutput testOutput) {
        this.testMode = (testOutput != null);
        this.testOutput = testOutput;
    }

    @Override
    public Object visitProgram(BabyCobolParser.ProgramContext ctx) {
        gotoLabel = null;
        // visit the data division first
        if (ctx.data() != null) {
            visitData(ctx.data());
        }
        // Visit the procedure division
        visit(ctx.procedure());

        // clean up
        sc.close();
        return null;
    }

    @Override
    public Object visitProcedure(BabyCobolParser.ProcedureContext ctx) {

        for (var sent: ctx.sentence()) {
            for (var st: sent.statement()) {
                if (st.gotoStatement() != null) {
                    gotoLabelsMap.put(st.gotoStatement(), st.gotoStatement().name().getText());
                }
            }
        }

        // Add all paragraphs as valid GOTO, PERFORM and SIGNAL
        for (BabyCobolParser.ParagraphContext pc : ctx.paragraph()) {
            // get the name of the paragraph
            String paraName = pc.label().getText();

            for (var sent: pc.sentence()) {
                for (var st: sent.statement()) {
                    if (st.gotoStatement() != null) {
                        gotoLabelsMap.put(st.gotoStatement(), st.gotoStatement().name().getText());
                    }
                }
            }

            // if paragraph not exists already add, else error
            if (!paragraphs.containsKey(paraName)) {
                paragraphs.put(paraName, pc);
            } else {
                throw new InterpreterException(ctx, "Paragraph with name: " + paraName + " is already declared!");
            }
        }

        // Index of paragraph iterator.
        int index = -1;

        boolean running = true;
        while (running) {
            try {
                // If we are re-iterating through the program after a SIGNAL, ignore the sentences outside paragraphs.
                if (signalParagraph == null) {
                    try {
                        // Execute the sentences in the procedure (Not directly the paragraphs)
                        for (int j = 0; j < ctx.sentence().size(); j++) {
                            visitSentence(ctx.sentence(j));
                        }
                    } catch (GotoException ex) {
                        if (gotoLabel != null) {
                            // Reset the index to the index of the goto label var.
                            index = ctx.paragraph().indexOf(paragraphs.get(gotoLabel));

                            // clean-up the gotoLabel var.
                            this.gotoLabel = null;
                        }
                    }
                }
               if (index == -1) {
                   index = 0;
               }

                // Execute the paragraphs in the procedure
                for (index = index; index < ctx.paragraph().size(); index++) {
                    var paragraph = ctx.paragraph(index);
                    for (int j = 0; j < paragraph.sentence().size(); j++) {
                        try {
                            visitSentence(paragraph.sentence(j));
                        } catch (GotoException ex) {
                            if (gotoLabel != null) {
                                // Reset the index to the index of the goto label var.
                                index = ctx.paragraph().indexOf(paragraphs.get(gotoLabel)) - 1;

                                // clean-up the gotoLabel var.
                                this.gotoLabel = null;

                                // The for loop will do i++ which starts visiting the first statement in the procedure.
                                break;
                            }
                        }
                    }
                }

                // Exit the while loop.
                running = false;
            } catch (Throwable throwable) {
                // if there is a signal paragraph label, aka there is a signal paragraph we can go to.
                if (signalParagraph == null) {
                    // No fallback method specified. Just throw the error.
                    throw new InterpreterException(throwable);
                }

                if (!paragraphs.containsKey(signalParagraph)) {
                    throw new InterpreterException(ctx, "The signalParagraph has an invalid identifier!");
                }

                // If another fatal error happens during the execution of the SIGNAL paragraph, it causes abnormal termination normally.
                if (ctx.paragraph().indexOf(paragraphs.get(signalParagraph)) == index) {
                    throw new InterpreterException(ctx, "Error in the signal paragraph, exiting program");
                }

                // Sent the program to the index of the signal paragraph.
                index = ctx.paragraph().indexOf(paragraphs.get(signalParagraph));
            }
        }
        return null;
    }

    /**
     * Lookup if there is a tree that has a node with the given path.
     * If there is one then return it's value.
     * If there are none then look into the variables map, which means that it was defined later.
     * If there are more than 1, then return null, which means that it's ambiguous.
     *
     * @param ctx are the identifiers that form a variable, i.e. A OF B OF C
     * @return null
     */
    @Override
    public Object visitIdentifiers(BabyCobolParser.IdentifiersContext ctx) {
        this.reset();
        int index = 1;
        if (ctx.INT() != null) {
            index = Integer.parseInt(ctx.INT().getText());
        }
        List<Tree> result = new ArrayList<>();
        for (var d : dataStructures) {
            result = Stream.concat(result.stream(), d.getNodesFromPath(ctx.getText(), new ArrayList<>()).stream())
                    .collect(Collectors.toList());
        }
        int totalResultsWithIndex = 0;
        Object res = new Object();
        for (var r : result) {
            if (r.getIndex() == index) {
                totalResultsWithIndex++;
                res = r.getValue();
            }
        }
        // In case the identifier we want to find also has OCCURS in the DATA DIVISION
        // we have to look it up using the index.
        // totalResultsWithIndex counts the amount of identifiers that are found that have the given index
        // If it's only one found then the given identifier is not ambiguous
        if (totalResultsWithIndex == 1) {
            return res;
        } else {
            // In case it's a variable not defined in the data division
            if (ctx.OF().size() == 0) {
                return this.getVariable(ctx.getText());
            }
        }

        throw new RuntimeException("Identifier " + ctx.getText() + " is too ambiguous");
    }

    @Override
    public Object visitSentence(BabyCobolParser.SentenceContext ctx) {
        try {
            ctx.statement().forEach(this::visitStatement);
        } catch (NextSentenceException ex) {
            // Stop with executing statements in this sentence and go to the next (if possible)
            return null;
        }
        return null;
    }

    @Override
    public Object visitSignal(BabyCobolParser.SignalContext ctx) {
        if (ctx.label() != null) {
            signalParagraph = ctx.label().getText();
        } else if (ctx.OFF() != null) {
            signalParagraph = null;
        }

        return null;
    }

    /**
     * Takes the variables defined in the DATA DIVISION and stores them as tree structures.
     *
     * @param ctx the identifiers defined in the data division
     * @return null
     */
    @Override
    public Object visitData(BabyCobolParser.DataContext ctx) {
        // Takes the starting level of the first variable, so there is a minimum
        int startingLevel = Integer.parseInt(ctx.variable().get(0).level().getText());
        for (var v : ctx.variable()) {
            int level = Integer.parseInt(v.level().getText());
            var value = v.IDENTIFIER().getText();
            var picture = v.representation();
            var like = v.identifiers();
            Tree likeNode = null;

            // If like keyword is used then lookup for that node
            if (like != null) {
                if (dataStructures.isEmpty()) {
                    throw new InterpreterException(ctx, "There is nothing to be like");
                }
                String path = like.getText();
                var result = this.getNodes(path);
                if (result.size() != 1) {
                    throw new InterpreterException(ctx, "Identifier given is too ambiguous, for " + v.getText());
                } else {
                    likeNode = result.get(0);
                }
            }

            // Sets the occurrence in case the keyword OCCURS is used.
            int occurences = 1;
            if (v.INT() != null) {
                occurences = Integer.parseInt(v.INT().getText());
            }

            // if it's the same level as the starting level then create a new tree,
            // else create new child for the current tree
            if (level == startingLevel) {
                root = new Tree(level, value, value);
                root.setOccurs(occurences);
                if (picture != null) {
                    if (picture.getText().contains("9")) {
                        root.setPicture(DataTypes.NINE.toString());
                    } else {
                        root.setPicture(DataTypes.X.toString());
                    }
                    root.setPictureSize(picture.getText().length());
                    switch (root.getPicture()) {
                        case X -> root.setValue(StringUtils.repeat(Character.toString((char) 0), root.getPictureSize()));
                        case NINE -> root.setValue(StringUtils.repeat("0", root.getPictureSize()));
                    }
                }
                if (likeNode != null) {
                    root.setLike(likeNode);
                }
                dataStructures.add(root);
            } else {
                Tree child = new Tree(level, value, value);
                child.setOccurs(occurences);
                if (picture != null) {
                    if (picture.getText().contains("9")) {
                        child.setPicture(DataTypes.NINE.toString());
                    } else {
                        child.setPicture(DataTypes.X.toString());
                    }
                    child.setPictureSize(picture.getText().length());
                    switch (child.getPicture()) {
                        case NINE -> child.setValue(StringUtils.repeat("0", child.getPictureSize()));
                        case X -> child.setValue(StringUtils.repeat(Character.toString((char) 0), child.getPictureSize()));
                    }
                }
                while (root.getLevel() >= level) {
                    root = root.getPrevious();
                }
                child.setPrevious(root);
                if (likeNode != null) {
                    child.setLike(likeNode);
                }
                root.addNext(child);
                root = child;
            }
        }
        // Reset the tree. This is just to make sure one of them is not at a different level than the minimum one.
        // Lookup for the nodes that have occurrences and duplicate them
        // Lookup for the nodes that are like other nodes and change their structure
        reset();
        addOccurrences();
        addLikes();
        return null;
    }


    @Override
    public Object visitStatement(BabyCobolParser.StatementContext ctx) {
        // TODO We should probably do the * and - here!
        return super.visitStatement(ctx);
    }

    @Override
    public Object visitStop(BabyCobolParser.StopContext ctx) {
        System.exit(0);
        return new Object();
    }

    /**
     * Moves the value of a given atomic, or constant, to a set of identifiers.
     * The constants are SPACES, LOW and HIGH
     *
     * @param ctx, the move command
     * @return null
     */
    @Override
    public Object visitMove(BabyCobolParser.MoveContext ctx) {
        // Define a string obj which will store which atomic or constant is used.
        String obj;
        Object toAssign = null;
        List<Tree> atomic = new ArrayList<>();
        Tree recordIdentifier = null;
        // Check if the first atomic is either atomic, LOW, HIGH or SPACES.
        if (ctx.atomic() != null) {
            // If it's atomic we check if it's and identifier or not.
            obj = "Atomic";
            if (ctx.atomic() instanceof BabyCobolParser.IdentifierContext) {
                // If it's an identifier first look for the node in the data structures with that name,
                // and check if it's a record or field
                // If it is a field we take it's value, else we take the node.
                for (var d : dataStructures) {
                    atomic = Stream.concat(atomic.stream(), d.getNodesFromPath(ctx.atomic().getText(),
                            new ArrayList<>()).stream()).collect(Collectors.toList());
                }
                if (atomic.size() == 1) {
                    if (atomic.get(0).isRecord()) {
                        recordIdentifier = atomic.get(0);
                    }
                } else {
                    throw new InterpreterException(ctx, "Ambiguous identifier!");
                }
            } else {
                toAssign = visit(ctx.atomic());
            }
        } else if (ctx.LOW() != null) {
            obj = "Low";
        } else if (ctx.HIGH() != null) {
            obj = "High";
        } else {
            obj = "Spaces";
        }
        var identifiers = ctx.identifiers();
        List<Tree> result;

        // We now start assigning the values to the identifiers
        for (var i : identifiers) {
            this.reset();
            // This index is used in case the identifier used has occurs, but it's not 100% working atm
            int index = 1;
            if (i.INT() != null) {
                index = Integer.parseInt(i.INT().getText());
            }
            // We first look if the identifiers were defined in the data division.
            result = new ArrayList<>();
            for (var d : dataStructures) {
                result = Stream.concat(result.stream(), d.getNodesFromPath(i.getText(), new ArrayList<>()).stream())
                        .collect(Collectors.toList());
            }
            int count = 0;
            Tree res = null;
            // We have the list of nodes and check if there is only one node with the given index.
            for (var r : result) {
                if (r.getIndex() == index) {
                    count++;
                    res = r;
                }
            }
            // If there is only one we start assigning the values.
            if (count == 1) {
                // If the atomic is a record then we take the leaves of the node and of the atomic.
                // If they are identical, same picture level and name, then the leaf gets the value.
                if (recordIdentifier != null) {
                    var recordIdentifierLeaves =
                            recordIdentifier.getLeaves(new HashMap<>(), 0);
                    var resultChildren =
                            res.getLeaves(new HashMap<>(), 0);
                    for (var a : recordIdentifierLeaves.keySet()) {
                        for (var r : resultChildren.keySet()) {
                            if (r.getName().equals(a.getName()) &&
                                    recordIdentifierLeaves.get(a).equals(resultChildren.get(r))) {
                                switch (r.getPicture()) {
                                    case NINE -> {
                                        if (NumberUtils.isCreatable(a.getValue())) {
                                            if (a.getValue().length() <= r.getPictureSize()) {
                                                r.setValue(StringUtils.repeat("0",
                                                        r.getPictureSize() - a.getValue().length()) +
                                                        a.getValue());
                                            } else {
                                                r.setValue(a.getValue().substring(a.getValue().length() -
                                                        r.getPictureSize()));
                                            }
                                        }
                                    }
                                    case X -> {
                                        if (a.getValue().length() <= r.getPictureSize()) {
                                            r.setValue(StringUtils.repeat(" ",
                                                    r.getPictureSize() - a.getValue().length()) + a.getValue());
                                        } else {
                                            r.setValue(a.getValue().substring(0, r.getPictureSize()));
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Else check first if an atomic or constant was used and based on the picture of the identifier
                    // assign a value.
                    switch (obj) {
                        case "Atomic":
                            this.setVariable(i.getText(), toAssign);
                            break;
                        case "Low":
                            switch (res.getPicture()) {
                                case NINE -> res.setValue(StringUtils.repeat("0", res.getPictureSize()));
                                case X -> res.setValue(StringUtils.repeat(Character.toString((char) 0),
                                        res.getPictureSize()));
                            }
                            break;
                        case "High":
                            switch (res.getPicture()) {
                                case NINE -> res.setValue(StringUtils.repeat("9", res.getPictureSize()));
                                case X -> res.setValue(StringUtils.repeat(Character.toString((char) 255),
                                        res.getPictureSize()));
                            }
                            break;
                        case "Spaces":
                            switch (res.getPicture()) {
                                case NINE -> res.setValue(StringUtils.repeat("0", res.getPictureSize()));
                                case X -> res.setValue(StringUtils.repeat(" ", res.getPictureSize()));
                            }
                            break;
                        default:
                            System.err.println("Error");
                            throw new InterpreterException(ctx, "Error");
                    }
                }
            } else if (result.size() == 0) {
                // In the case the identifier is not part of the data division then check if the OF keyword is used or not.
                // If it's not used, then add the value to the identifier and save it in the variable map.
                if (i.OF().size() == 0) {
                    this.setVariable(i.getText(), toAssign);
                }
            } else {
                System.err.println("Identifiers are too ambiguous, for " + i.getText());
                throw new InterpreterException(ctx, "Identifiers are too ambiguous, for " + i.getText());
            }
        }
        return null;
    }


    /**
     * Subtract the sum of a list of atomics from another atomic.
     *
     * @param ctx the subtract command
     * @return null
     */
    @Override
    public Object visitSubtract(BabyCobolParser.SubtractContext ctx) {
        var atomics = ctx.as;
        var from = ctx.from;
        var identifier = ctx.giving;
        if (from instanceof BabyCobolParser.IntLiteralContext && identifier == null) {
            // TODO: If this means ERROR, then throw one!
            System.out.println("GIVING identifier is required!");
            return new Object();
        }
        //Calculate the sum of the atomics
        int sum = 0;
        for (var a : atomics) {
            if (a instanceof BabyCobolParser.IdentifierContext) {
                if (this.hasPictureNine(((BabyCobolParser.IdentifierContext) a).identifiers().getText())) {
                    var value = visit(a);
                    sum += Integer.parseInt(value.toString());
                } else {
                    throw new RuntimeException("Cannot subtract identifier with picture different than 9");
                }
            } else {
                var value = visit(a);
                try {
                    sum += Integer.parseInt(value.toString());
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Cannot subtract a non-numeric value");
                }
            }
        }
        //Calculate the result of the subtraction
        var result = 0;
        if (from instanceof BabyCobolParser.IdentifierContext) {
            if (this.hasPictureNine(((BabyCobolParser.IdentifierContext) from).identifiers().getText())) {
                result = Integer.parseInt(visit(from).toString()) - sum;
            } else {
                throw new RuntimeException("Cannot subtract identifier with picture different than 9");
            }
        } else {
            try {
                result = Integer.parseInt(visit(from).toString()) - sum;
            } catch (NumberFormatException e) {
                throw new RuntimeException("Cannot subtract from a non-numeric value");
            }
        }

        //Look up for the variable given by the atomic from.
        //Also if identifier is not null look up that variable as well
        List<Tree> vars = new ArrayList<>();
        List<Tree> fromVars = new ArrayList<>();
        for (var d : dataStructures) {
            vars = Stream.concat(vars.stream(), d.getNodesFromPath(from.getText(),
                    new ArrayList<>()).stream()).collect(Collectors.toList());
            if (identifier != null) {
                fromVars = Stream.concat(fromVars.stream(), d.getNodesFromPath(identifier.getText(),
                        new ArrayList<>()).stream()).collect(Collectors.toList());
            }
        }

        //Store the value into the given identifier or the from atomic.
        if (identifier != null) {
            this.setVariable(identifier.getText(), result);
        } else {
            this.setVariable(from.getText(), result);
        }
        return new Object();
    }

    /**
     * Multiply atomic a by a list of atomics and store the product in the atomics.
     * If GIVING keyword is used then store it in the identifier given.
     *
     * @param ctx the multiply command
     * @return null
     */
    @Override
    public Object visitMultiply(BabyCobolParser.MultiplyContext ctx) {
        var atomic = ctx.a;
        var atomics = ctx.as;
        var identifier = ctx.giving;

        // Get the value of the first atomic
        int product;
        if (atomic instanceof BabyCobolParser.IdentifierContext) {
            if (this.hasPictureNine(((BabyCobolParser.IdentifierContext) atomic).identifiers().getText())) {
                product = Integer.parseInt(visit(atomic).toString());
            } else {
                throw new RuntimeException("Cannot multiply an identifier with picture different than 9");
            }
        } else {
            try {
                product = Integer.parseInt(atomic.getText());
            } catch (NumberFormatException e) {
                throw new RuntimeException("Atomic has non-numeric value");
            }
        }

        // Multiply the list of atomics
        // If the identifier is not given store the product to the atomic and then do it again.
        // It does it again because if more atomics are given the second atomic will
        // use the new value of the previous one in the product.
        int prod = 1;
        for (var a : atomics) {
            prod = 1;
            for (var b : atomics) {
                if (b instanceof BabyCobolParser.IdentifierContext) {
                    if (this.hasPictureNine(((BabyCobolParser.IdentifierContext) b).identifiers().getText())) {
                        prod *= Integer.parseInt(visit(b).toString());
                    } else {
                        throw new RuntimeException("Cannot multiply an identifier with picture different than 9");
                    }
                } else {
                    try {
                        prod *= Integer.parseInt(visit(b).toString());
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("Cannot multiply with non-numeric value");
                    }
                }
            }
            if (identifier == null) {
                this.setVariable(a.getText(), prod * product);
            }
        }
        // Get the variable from giving if it is not null.
        if (identifier != null) {
            this.setVariable(identifier.getText(), prod * product);
        }

        return new Object();
    }

    /**
     * Takes the starting label, ending label if the keyword through was used and the number of times (default 1).
     * If an ending label is given it executes all the statement starting with the first paragraph given through the
     * last one.
     * The statements are taken from a map that has the label as key and the list of statements as value.
     *
     * @param ctx the perform command
     * @return null
     */
    @Override
    public Object visitPerform(BabyCobolParser.PerformContext ctx) {
        //Takes the label of the paragraph it needs to perform
        var startLabel = visitLabel(ctx.procedureName);

        if (!paragraphs.containsKey(startLabel)) {
            throw new InterpreterException(ctx, "label: " + startLabel +  "does not exist");
        }

        //Takes the number of times it needs to perform it
        int repeat = 1;

        if (ctx.times != null) {
            try {
                repeat = Integer.parseInt(visit(ctx.times).toString());
            } catch (NumberFormatException e) {
                throw new InterpreterException(e);
            }
        }

        //Gets the list of sentences from the paragraph
        var sentences = paragraphs.get(startLabel).sentence();
        var startIndex = 0;
        var endIndex = 0;
        //Save the names of the labels that will be performed in the perform through
        List<String> labelsToBeExecuted = new ArrayList<>();
        if (ctx.through != null) {
            //Takes the end label in case there is the through keyword given
            var endLabel = ctx.through.getText().trim();
            var set = Arrays.asList(paragraphs.keySet().toArray());

            if (!paragraphs.containsKey(endLabel)) {
                throw new InterpreterException(ctx, "label: " + startLabel +  "does not exist");
            }
            /* Looks through the map containing the labels and their sentences
             * and stores as indexes the starting position of the first and last label given.
             */
            for (int i = 0; i < set.size(); i++) {
                var entry = set.get(i).toString().trim();
                if (entry.equals(startLabel)) {
                    startIndex = i;
                }
                if (entry.equals(endLabel)) {
                    endIndex = i;
                }
            }
            for (int i = startIndex; i <= endIndex; i++) {
                labelsToBeExecuted.add(set.get(i).toString());
            }
            // Create o list of sentences from the paragraphs given
            // Start from startIndex + 1, because it already contains all the sentences from the first index.
            for (int i = startIndex + 1; i <= endIndex; i++) {
                sentences = Stream.concat(sentences.stream(), paragraphs.get(set.get(i).toString()).sentence().stream()).
                        collect(Collectors.toList());
            }
        }
        // Perform the paragraph an X amount of times
        for (int index = 0; index < repeat; index++) {
            for (var sentence: sentences) {
                for (var statement: sentence.statement()) {
                    if (statement.gotoStatement() != null) {
                        String labelName = gotoLabelsMap.get(statement.gotoStatement());
                        if(String.valueOf(getVariable(gotoLabelsMap.get(statement.gotoStatement()))) != null) {
                            labelName = String.valueOf(getVariable(gotoLabelsMap.get(statement.gotoStatement())));
                        }

                        if (labelsToBeExecuted.contains(labelName)) {
                            visit(paragraphs.get(labelName));
                            return null;
                        }
                    }
                }
                visit(sentence);
            }
        }

        return null;
    }


    @Override
    public Object visitDisplay(BabyCobolParser.DisplayContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (var atomic : ctx.atomic()) {
            var value = visit(atomic);
            if (value == null) {
                return null;
            }
            sb.append(" ").append(value);
        }
        String printString = sb.substring(1);
        System.out.print(printString);

        if (testMode) {
            testOutput.addToList(new Tuple<>(printString, ctx.getStart().getLine()));
        }

        if (ctx.WITH() == null && ctx.NO() == null && ctx.ADVANCING() == null) { //
            System.out.println();
        }
        return null;
    }

    /**
     * Get a string form of the expression given and the evaluates it, which returns a boolean.
     * If it's true execute the statements given, else if statements are given for the else, they are executed instead.
     *
     * @param ctx, which is the if statement
     * @return null
     */
    @Override
    public Object visitIfStatement(BabyCobolParser.IfStatementContext ctx) {
        var expression = visit(ctx.booleanExpression()).toString();
        var value = evaluateBooleanExpression(expression);
        if (value) {
            for (var s : ctx.t) {
                visitStatement(s);
            }
        } else {
            if (ctx.f != null) {
                for (var s : ctx.f) {
                    visitStatement(s);
                }
            }
        }
        return null;
    }

    public Object visitIdentifier(BabyCobolParser.IdentifierContext ctx) {
        return visit(ctx.identifiers());
    }

    @Override
    public String visitLabel(BabyCobolParser.LabelContext ctx) {
        return ctx.IDENTIFIER().getText();
    }

    @Override
    public Object visitAccept(BabyCobolParser.AcceptContext ctx) {
        String val;
        var identifiers = ctx.id;
        for (var i : identifiers) {
            val = sc.next();
            this.setVariable(i.getText(), val);
        }
        return null;
    }


    /**
     * Two or more numeric values that should be stored
     * <p>
     * - all three arguments obey the rules of sufficient qualification
     * - either of the first two arguments can be a literal
     * - if the second argument is a literal, the third argument is mandatory
     * - any of the three arguments can be an identifier defined with a numeric picture clause (free from A and X)
     *
     * @param ctx context
     * @return -
     */
    @Override
    public Object visitAdd(BabyCobolParser.AddContext ctx) {
        int sum = 0;
        StringBuilder result = new StringBuilder();
        boolean concat = false;

        if (ctx.to instanceof BabyCobolParser.IdentifierContext) {
            if (!this.hasPictureNine(((BabyCobolParser.IdentifierContext) ctx.to).identifiers().getText())) {
                concat = true;
            }
        }

        for (BabyCobolParser.AtomicContext atomic : ctx.atomic()) {
            if (concat) {
                result.append(visit(atomic).toString().trim());
            } else {
                sum += Integer.parseInt(visit(atomic).toString());
            }
        }

        // There is no giving clause and there is no literal present as second argument, throw error.
        if (ctx.id == null && !this.containsVariable(ctx.to.getText())) {
            throw new InterpreterException(ctx, "GIVING clause not provided");
        }
        // There is no giving clause present but there is a variable present as second argument.
        else if (ctx.id == null && this.containsVariable(ctx.to.getText())) {
            if (concat) {
                this.setVariable(ctx.to.getText(), result.toString());
            } else {
                this.setVariable(ctx.to.getText(), sum);
            }
        }
        // There is a giving clause, so assign the sum to this variable.
        else {
            if (concat) {
                this.setVariable(ctx.id.getText(), result.toString());
            } else {
                this.setVariable(ctx.id.getText(), sum);
            }
        }

        return null;
    }

    /**
     * Divides the second value into the first value and (re)saves the first value as a variable.
     * <p>
     * - all four arguments obey the rules of sufficient qualification
     * - either of the first two arguments can be a literal
     * - if the first argument is a literal, the third argument is mandatory
     * - any of the four arguments can be an identifier defined with a numeric picture clause (free from A and X)
     *
     * @param ctx context
     * @return -
     */
    @Override
    public Object visitDivide(BabyCobolParser.DivideContext ctx) {
        int prod = 1;
        for (var a: ctx.as) {
            if (a instanceof BabyCobolParser.IdentifierContext) {
                if (this.hasPictureNine(((BabyCobolParser.IdentifierContext) a).identifiers().getText())) {
                    prod *= Integer.parseInt(visit(a).toString());
                } else {
                    throw new RuntimeException("Cannot divide with an identifier with picture different than 9");
                }
            } else {
                try {
                    prod *= Integer.parseInt(visit(a).toString());
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Cannot divide with non-numeric atomic");
                }
            }
        }

        int value;
        if (ctx.a instanceof BabyCobolParser.IdentifierContext) {
            if (this.hasPictureNine(((BabyCobolParser.IdentifierContext) ctx.a).identifiers().getText())) {
                value = Integer.parseInt(visit(ctx.a).toString());
            } else {
                throw new RuntimeException("Cannot divide identifier with picture different than 9");
            }
        } else {
            try {
                value = Integer.parseInt(visit(ctx.a).toString());
            } catch (NumberFormatException e) {
                throw new RuntimeException("Cannot divide non-numeric atomic");
            }
        }

        // There is no giving clause and there is no literal present as first argument, throw error.
        if (ctx.id == null && !this.containsVariable(ctx.atomic(0).getText())) {
            throw new InterpreterException(ctx, "GIVING clause not provided");
        }
        // There is no giving clause present but there is a variable present as first argument.
        else if (ctx.id == null && this.containsVariable(ctx.atomic(0).getText())) {
            this.setVariable(ctx.atomic(0).getText(), value / prod);
        }
        // There is a giving clause, so assign the sum to this variable.
        else {
            this.setVariable(ctx.id.getText(), value / prod);
        }

        if (ctx.rem != null) {
            int remainder = value % prod;

            this.setVariable(ctx.rem.getText(), remainder);
        }

        return null;
    }


    /**
     * EVALUATE AnyExpression WhenBlock* END
     * <p>
     * Essentially, the role of the EVALUATE statement is to bring infix expressions into the language, and they are
     * needed to force the software language engineer to deal with parsing ambiguities between A-B and A - B where the
     * first one is a name of a single field and the second one is a binary subtraction of two fields (spaces serve a
     * demonstrative purpose in this example, and are ignored during by the lexical analyser).
     *
     * @param ctx context
     * @return -
     */
    @Override
    public Object visitEvaluate(BabyCobolParser.EvaluateContext ctx) {

        // Save which evaluate object this EVALUATE statement should check its WHEN statements against.
        this.evaluateObject = visit(ctx.anyExpression());

        Supplier<Stream<BabyCobolParser.WhenBlockContext>> whenOtherBlockStream
                = () -> ctx.whenBlock().stream().filter(whenBlock -> whenBlock instanceof BabyCobolParser.WhenOtherContext);

        // Multiple WHEN OTHER states not allowed TODO: Ask if this is correct assumption
        if (whenOtherBlockStream.get().count() > 1) {
            throw new InterpreterException(ctx, "Multiple WHEN OTHER parts not allowed");
        }

        // Variable to remember if one of the WHEN blocks was entered so WHEN OTHER can be ignored.
        boolean hasEnteredWhenBlock = false;

        // Visit every atomic WHEN block first.
        for (BabyCobolParser.WhenBlockContext whenBlock : ctx.whenBlock()) {
            if (whenBlock instanceof BabyCobolParser.WhenAnyExpressionContext) {
                if ((Boolean) visit(whenBlock)) {
                    hasEnteredWhenBlock = true;
                }
            }
        }

        // Check if no atomic WHEN block was successfully entered
        if (!hasEnteredWhenBlock) {
            if (whenOtherBlockStream.get().findFirst().isPresent()) {
                BabyCobolParser.WhenOtherContext otherBlock
                        = (BabyCobolParser.WhenOtherContext) whenOtherBlockStream.get().findFirst().get();

                // Now visit the WHEN OTHER block and every WHEN block after it to also check if they can be reached
                // after the WHEN OTHER has (possibly) updated the data.
                for (int i = ctx.whenBlock().indexOf(otherBlock); i < ctx.whenBlock().size(); i++) {
                    BabyCobolParser.WhenBlockContext whenBlock = ctx.whenBlock(i);

                    visit(whenBlock);

                    // We should reset the evaluateObject since it may have been impacted by the OTHER statement
                    if (ctx.whenBlock(i) instanceof BabyCobolParser.WhenOtherContext) {
                        this.evaluateObject = visit(ctx.anyExpression());
                    }
                }

            }
        }

        // clean-up
        this.evaluateObject = null;

        return null;
    }

    @Override
    public Object visitAnyExpression(BabyCobolParser.AnyExpressionContext ctx) {
        if (ctx.booleanExpression() != null) {
            return evaluateBooleanExpression(visit(ctx.booleanExpression()).toString());
        } else if (ctx.arithmeticExpression() != null) {
            return evaluateArithmeticExpression(visit(ctx.arithmeticExpression()).toString());
        } else if (ctx.stringExpression() != null) {
            return visit(ctx.stringExpression());
        } else {
            throw new InterpreterException(ctx, "Missing type of AnyExpression in visitAnyExpression()!");
        }
    }

    @Override
    public Object visitWhenOther(BabyCobolParser.WhenOtherContext ctx) {
        return super.visitWhenOther(ctx);
    }

    /**
     * @param ctx context
     * @return TRUE if the WHEN was entered
     */
    @Override
    public Boolean visitWhenAnyExpression(BabyCobolParser.WhenAnyExpressionContext ctx) {
        boolean proceed = false;

        for (BabyCobolParser.AnyExpressionContext any : ctx.anyExpression()) {
            if (visit(any).toString().equals(this.evaluateObject.toString())) {
                proceed = true;
            }
        }

        // Only proceed when this atomic is allowed to continue within the evaluate block
        if (proceed) {
            for (BabyCobolParser.StatementContext statement : ctx.statement()) {
                visitStatement(statement);
            }
        }

        return proceed;
    }

    @Override
    public Object visitNextSentence(BabyCobolParser.NextSentenceContext ctx) throws NextSentenceException {
        throw new NextSentenceException();
    }

    @Override
    public Object visitLoop(BabyCobolParser.LoopContext ctx) {
        this.currentLoop = new Loop();
        int index = 0;

        while (!currentLoop.exitLoop() || index != 0) {
            visit(ctx.loopExpression(index));
            index = (index + 1) % ctx.loopExpression().size();
        }

        this.currentLoop = null;

        return null;
    }

    @Override
    public Object visitLoopStatement(BabyCobolParser.LoopStatementContext ctx) {
        return visit(ctx.statement());
    }

    /**
     * VARYING id=IDENTIFIER? (FROM from=atomic)? (TO to=atomic)? (BY by=atomic)?
     *
     * @param ctx context
     * @return -
     */
    @Override
    public Object visitVaryingLoopExp(BabyCobolParser.VaryingLoopExpContext ctx) {
        if (!this.currentLoop.hasVarying()) {
            // Assume in case there is no picture the maximum value is 9
            int maxValue = 0;
            if (ctx.id != null) {
                List<Tree> id = new ArrayList<>();
                // Retrieve all the identifiers with the path given by the id
                for (var d : dataStructures) {
                    id = Stream.concat(d.getNodesFromPath(ctx.id.getText(), new ArrayList<>()).stream(),
                            id.stream()).collect(Collectors.toList());
                }
                // If size is one then there is no ambiguity, else throw errors in case there is ambiguity.
                if (id.size() == 1) {
                    if (id.get(0).getPicture() != null) {
                        switch (id.get(0).getPicture()) {
                            // If it has picture X then throw an error,
                            // else set the max value based on the picture of the id.
                            case X -> {
                                throw new RuntimeException("Cannot give non-numeric value");
                            }
                            case NINE -> {
                                maxValue = Integer.parseInt(StringUtils.repeat("9", id.get(0).getPictureSize()));
                            }
                        }
                    } else {
                        throw new InterpreterException(ctx, "Cannot give identifier without picture!");
                    }
                } else {
                    if (ctx.to == null)
                        throw new InterpreterException(ctx, "Given identifier " + ctx.id.getText() + " has no picture so a maximum loop value cannot be given!");
                }
            }
            this.currentLoop.initVarying(
                    ctx.from != null ? (Integer) visit(ctx.from) : 1,
                    ctx.to != null ? (Integer) visit(ctx.to) : maxValue,
                    ctx.by != null ? (Integer) visit(ctx.by) : 1
            );
        }

        // Update index variable
        setVariable(ctx.id.getText(), this.currentLoop.getVaryingValue());

        currentLoop.increment();

        return null;
    }


    @Override
    public Object visitWhileLoopExp(BabyCobolParser.WhileLoopExpContext ctx) {
        if (!evaluateBooleanExpression(visit(ctx.booleanExpression()).toString())) {
            this.currentLoop.exit();
        }
        return null;
    }

    @Override
    public Object visitUntilLoopExp(BabyCobolParser.UntilLoopExpContext ctx) {
        if (evaluateBooleanExpression(visit(ctx.booleanExpression()).toString())) {
            this.currentLoop.exit();
        }
        return null;
    }

    /**
     * GO TO procedureName
     * a statement to branch unconditionally to a paragraph within a program
     *
     * @param ctx context
     * @return -
     */
    @Override
    public Object visitGotoStatement(BabyCobolParser.GotoStatementContext ctx) throws GotoException {
        String labelName;
        if (gotoLabelsMap.containsKey(ctx)) {
            labelName = gotoLabelsMap.get(ctx);
        } else {
            labelName = ctx.name().IDENTIFIER().getText();
        }

        // Lookup for variable with identifier 'labelName', assign to labelName if it exists.
        if(String.valueOf(getVariable(labelName)) != null) {
            labelName = String.valueOf(getVariable(labelName));
        }

        if (paragraphs.containsKey(labelName)) {
            // Set goto label to procedure name or computed name
            this.gotoLabel = labelName;

            // When done with the goto statement, we signal the program that we don't want to anything else but handle the goto
            throw new GotoException();
        }

        throw new InterpreterException(ctx, "Paragraph " + labelName + " not found");
    }


    @Override
    public Integer visitIntLiteral(BabyCobolParser.IntLiteralContext ctx) {
        return Integer.parseInt(ctx.INT().getText());
    }

    @Override
    public String visitStringLiteral(BabyCobolParser.StringLiteralContext ctx) {
        return ctx.LITERAL().getText().substring(1, ctx.getText().length() - 1);
    }

    @Override
    public Object visitAtomicArithmeticExp(BabyCobolParser.AtomicArithmeticExpContext ctx) {
        return visit(ctx.atomic());
    }

    @Override
    public String visitArithOpArithmeticExp(BabyCobolParser.ArithOpArithmeticExpContext ctx) {
        String equation = "";
        var leftValue = visit(ctx.left).toString();
        var rightValue = visit(ctx.right).toString();
        equation += (leftValue + ctx.arithmeticOp().getText() + rightValue);
        return equation;
    }

    @Override
    public String visitAtomicStringExp(BabyCobolParser.AtomicStringExpContext ctx) {
        return visit(ctx.atomic()).toString();
    }

    @Override
    public String visitAdditionStringExp(BabyCobolParser.AdditionStringExpContext ctx) {
        return visit(ctx.left).toString() + visit(ctx.right).toString();
    }

    @Override
    public Boolean visitTrueBooleanExp(BabyCobolParser.TrueBooleanExpContext ctx) {
        return Boolean.TRUE;
    }

    @Override
    public Boolean visitFalseBooleanExp(BabyCobolParser.FalseBooleanExpContext ctx) {
        return Boolean.FALSE;
    }

    @Override
    public String visitBoolOpBooleanExp(BabyCobolParser.BoolOpBooleanExpContext ctx) {
        var leftValue = visit(ctx.left);
        var rightValue = visit(ctx.right);
        String equation = "";
        equation += (leftValue.toString() + ctx.booleanOp().getText() + rightValue.toString());
        return equation;
    }

    @Override
    public String visitNotBooleanExp(BabyCobolParser.NotBooleanExpContext ctx) {
        String equation = "!(";
        equation += visit(ctx.booleanExpression());
        equation += ")";
        return equation;
    }

    @Override
    public String visitCompareOpBooleanExp(BabyCobolParser.CompareOpBooleanExpContext ctx) {
        var leftValue = visit(ctx.left);
        var rightValue = visit(ctx.right);
        return leftValue + ctx.comparisonOp().getText() + rightValue;
    }

    @Override
    public String visitContractedBooleanExp(BabyCobolParser.ContractedBooleanExpContext ctx) {
        var leftValue = visit(ctx.left);
        var rightValue = visit(ctx.right);

        // Start building the normal part of the expression
        StringBuilder equation = new StringBuilder();
        equation.append(leftValue);
        previousContractedComparisonOp = ctx.comparisonOp().getText();
        equation.append(previousContractedComparisonOp);
        equation.append(rightValue);

        // Get all contracted parts
        ArrayList<Object> array = new ArrayList<>();
        for (ParseTree s : ctx.contract) {
            ArrayList<String> list = (ArrayList<String>) visit(s);
            array.add(list.get(0));
            array.add(list.get(1));
            array.add(list.get(2));
        }

        // Insert the left value where it is missing
        for (int i = 0; i < array.size(); i++) {
            if (i % 3 == 1) {
                // insert left first
                equation.append(leftValue);
            }
            equation.append(array.get(i));
        }

        // reset the variable
        previousContractedComparisonOp = null;
        return equation.toString();
    }

    @Override
    public ArrayList<String> visitContractedBooleanPart(BabyCobolParser.ContractedBooleanPartContext ctx) {
        ArrayList<String> array = new ArrayList<>();
        array.add(ctx.booleanOp().getText());
        // if this contractedBooleanPart does not contain a comparisonOP we have to use the same as in the previous part
        if (ctx.comparisonOp() == null) {
            array.add(previousContractedComparisonOp);
        } else {
            // If it does have one we set this globally and add it to the array
            String previousContractedComparisonOp = ctx.comparisonOp().getText();
            array.add(previousContractedComparisonOp);
        }
        array.add(evaluateArithmeticExpression(ctx.arithmeticExpression().getText()).toString());
        return array;

    }

    @Override
    public Object visitAlter(BabyCobolParser.AlterContext ctx) {
        var label1 = ctx.l1.getText().trim();
        var label2 = ctx.l2.getText().trim();
        BabyCobolParser.GotoStatementContext gotos = null;
        if (paragraphs.containsKey(label1) && paragraphs.containsKey(label2)) {
            if (paragraphs.get(label1).sentence().size() == 1
                    && paragraphs.get(label1).sentence().get(0).statement().size() == 1
                    && paragraphs.get(label1).sentence().get(0).statement().get(0).gotoStatement() != null) {
                gotos = paragraphs.get(label1).sentence().get(0).statement().get(0).gotoStatement();
                gotoLabelsMap.replace(gotos, label2);
            }
        } else {
            throw new InterpreterException(ctx, "Label does not exist!");
        }
        return null;
    }


    /**
     * ==============
     * HELPER METHODS
     * ==============
     */

    public Integer evaluateArithmeticExpression(String expression) {
        expression = expression.replace("**", "^");

        ScriptEngineManager manager = new ScriptEngineManager();
        var engine = manager.getEngineByName("js");
        engine.put("expression", expression);
        try {
            engine.put("processed", engine.eval("expression.replace(/(\\w+)\\^(\\w+)/g, 'Math.pow($1,$2)')"));
            expression = engine.get("processed").toString();
            var number = engine.eval(expression);
            if (number instanceof Double) {
                return (int) ((double) number);
            } else {
                return (int) number;
            }
        } catch (ScriptException e) {
            System.out.println("An error occured");
            return -1;
        }
    }

    /**
     * Takes a boolean expression as a string and through ScriptEngineManager evaluates it.
     * Before evaluation some symbols need to be changed, so it is equivalent to javascript boolean expression.
     * @param expression the boolean expression that needs to be avaluated
     * @return true or false, based on the result of it
     */
    public Boolean evaluateBooleanExpression(String expression) {
        expression = expression.replace("=", "==");
        expression = expression.replace("!==", "!=");
        expression = expression.replace(">==", ">=");
        expression = expression.replace("<==", "<=");
        expression = expression.replace("**", "^");

        ScriptEngineManager manager = new ScriptEngineManager();
        var engine = manager.getEngineByName("js");
        engine.put("expression", expression);
        try {
            engine.put("processed", engine.eval("expression.replace(/(\\w+)\\^(\\w+)/g, 'Math.pow($1,$2)')"));
            expression = engine.get("processed").toString();
            expression = expression.replace("AND", "&&");
            expression = expression.replace("OR", "||");
            expression = expression.replace("XOR", "^");
            return (Boolean) engine.eval(expression);
        } catch (ScriptException e) {
            System.out.println("An error occured");
            return false;
        }
    }

    void setVariable(String name, Object val) {
        List<Tree> nodes = new ArrayList<>();
        for (var d: dataStructures) {
            nodes = Stream.concat(nodes.stream(), d.getNodesFromPath(name, new ArrayList<>()).stream())
                    .collect(Collectors.toList());
        }
        if (nodes.size() == 1) {
            if (nodes.get(0).isRecord()) {
                throw new RuntimeException("Cannot assign value to a record!");
            }
            if (nodes.get(0).getPicture() != null) {
                switch (nodes.get(0).getPicture()) {
                    case X -> {
                        if (val.toString().length() < nodes.get(0).getPictureSize()) {
                            nodes.get(0).setValue(StringUtils.repeat(" ",
                                    nodes.get(0).getPictureSize() - val.toString().length()));
                            nodes.get(0).setValue(nodes.get(0).getValue() + val.toString());
                        } else {
                            nodes.get(0).setValue(val.toString().substring(0, nodes.get(0).getPictureSize()));
                        }
                    }
                    case NINE -> {
                        if (NumberUtils.isCreatable(val.toString())) {
                            if (val.toString().length() < nodes.get(0).getPictureSize()) {
                                nodes.get(0).setValue(StringUtils.repeat("0",
                                        nodes.get(0).getPictureSize() - val.toString().length()) + val.toString());
                            } else {
                                nodes.get(0).setValue(val.toString().
                                        substring(val.toString().length() - nodes.get(0).getPictureSize()));
                            }
                        } else {
                            throw new RuntimeException("Non-numeric value cannot be assigned to identifier with picture of type 9");
                        }
                    }
                }
            } else {
                nodes.get(0).setValue(val.toString());
            }
        } else if (nodes.size() == 0 && !name.contains("OF")) {
            this.variables.put(name.toLowerCase(Locale.ROOT), val);
        } else {
            throw new RuntimeException("Ambiguous Identifier given " + name);
        }
    }

    Object getVariable(String name) {
        List<Tree> nodes = new ArrayList<>();
        for (var d: dataStructures) {
            nodes = Stream.concat(nodes.stream(), d.getNodesFromPath(name, new ArrayList<>()).stream()).collect(Collectors.toList());
        }
        if (nodes.size() == 1) {
            return nodes.get(0).getName();
        }
        if (!this.variables.containsKey(name.toLowerCase(Locale.ROOT))) {
            this.setVariable(name, name.toUpperCase());
            return name.toUpperCase();
        }
        return this.variables.get(name.toLowerCase(Locale.ROOT));
    }

    boolean containsVariable(String name) {
        List<Tree> nodes = new ArrayList<>();
        for (var d: dataStructures) {
            nodes = Stream.concat(nodes.stream(), d.getNodesFromPath(name, new ArrayList<>()).stream()).collect(Collectors.toList());
        }
        if (nodes.size() >= 1) {
            return true;
        }
        return this.variables.containsKey(name.toLowerCase(Locale.ROOT));
    }


    public void reset() {
        for (var dataStructure : dataStructures) {
            while (dataStructure.getPrevious() != null) {
                dataStructure = dataStructure.getPrevious();
            }
        }
    }

    public List<Tree> getNodes(String path) {
        reset();
        List<Tree> result = new ArrayList<>();
        for (var d : dataStructures) {
            result = Stream.concat(result.stream(), d.getNodesFromPath(path, new ArrayList<>()).stream())
                    .collect(Collectors.toList());
        }
        return result;
    }

    public void addOccurrences() {
        for (var d : dataStructures) {
            var result = d.getNodesWithOccurs(new ArrayList<>());
            for (var r : result) {
                for (int i = 1; i < r.getOccurs(); i++) {
                    Tree child = new Tree(r.getLevel(), r.getValue(), r.getName());
                    child.setPicture(r.getPicture().toString());
                    child.setNext(r.getNext());
                    child.setIndex(i + 1);
                    if (r.getPrevious() != null) {
                        r.getPrevious().addNext(child);
                    } else {
                        dataStructures.add(child);
                    }
                }
            }
        }
    }

    public void addLikes() {
        for (var d : dataStructures) {
            var result = d.getNodesWithLikes(new ArrayList<>());
            for (var r : result) {
                for (var c: r.getLike().getNext()) {
                    r.addNext(c.deepCopy());
                }
                r.resetNode();
            }
        }
    }

    private boolean hasPictureNine(String identifierName) {
        List<Tree> nodes = new ArrayList<>();
        for (var d: dataStructures) {
            nodes = Stream.concat(nodes.stream(), d.getNodesFromPath(identifierName, new ArrayList<>()).stream())
                    .collect(Collectors.toList());
        }
        if (nodes.size() == 1) {
            return nodes.get(0).getPicture().equals(DataTypes.NINE);
        } else return nodes.size() == 0;
    }
}