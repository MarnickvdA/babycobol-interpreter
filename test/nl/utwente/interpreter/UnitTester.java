package nl.utwente.interpreter;

import nl.utwente.interpreter.model.Tuple;
import nl.utwente.interpreter.exception.InterpreterException;
import nl.utwente.interpreter.Interpreter;
import nl.utwente.interpreter.model.ProgramOutput;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class UnitTester {

    private static CharStream fetchStreamForFile(String file) throws IOException {
        String str = "./test/nl/utwente/interpreter/testfiles/" + file;
        File streamFile = new File(str);
        return CharStreams.fromPath(streamFile.toPath());
    }

    Interpreter interpreter;
    ProgramOutput programOutput;

    @BeforeEach
    private void init() {
        this.interpreter = new Interpreter();
        this.programOutput = new ProgramOutput();
    }

    public void testEquivalence(List<String> expected, ArrayList<Tuple<String, Integer>> actual) {
        int i = 0;
        for (Tuple<String, Integer> t : actual) {
            assertEquals(expected.get(i), t._x, "Unexpected output at line: " + t._y);
            i++;
        }
    }

    @Test
    public void testAdd() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("9");

        interpreter.compile(fetchStreamForFile("add.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(actual.size(), expected.size(), "actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    public void testDivide() throws IOException {

        ArrayList<String> expected = new ArrayList<>();
        expected.add("1");
        expected.add("2");

        interpreter.compile(fetchStreamForFile("divide.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(expected.size(), actual.size(), "Actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    public void testMultiply() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("50");

        interpreter.compile(fetchStreamForFile("multiply.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(actual.size(), expected.size(), "actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    public void testSubtract() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("3");

        interpreter.compile(fetchStreamForFile("subtract.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(actual.size(), expected.size(), "actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    // NOTE: This is not a good test for the method we rely on for all our other tests....
    @Test
    public void testDisplay() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("hello world");
        expected.add("numerical values below");
        expected.add("1 2 3 100");
        expected.add("this display works correctly and has printed 3 statements before this one");

        interpreter.compile(fetchStreamForFile("display.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(expected.size(), actual.size(), "Actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    public void testEvaluate() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("3");
        expected.add("WHEN OTHER was reached!");
        expected.add("4");
        expected.add("Reached WHEN OTHER and now subtracting to get A=3");
        expected.add("3");

        interpreter.compile(fetchStreamForFile("evaluate.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(expected.size(), actual.size(), "Actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    public void testLoop() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("1");
        expected.add("2");
        expected.add("3");
        expected.add("4");
        expected.add("5");

        interpreter.compile(fetchStreamForFile("loop.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(expected.size(), actual.size(), "Actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    public void testNextSentence() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("1");
        expected.add("2");

        interpreter.compile(fetchStreamForFile("nextsentence.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(expected.size(), actual.size(), "Actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    public void testGoto() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("A");
        expected.add("B");
        expected.add("C");
        expected.add("D");

        interpreter.compile(fetchStreamForFile("goto.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(expected.size(), actual.size(), "Actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    public void testGotoLoop() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("COUNT");
        expected.add("1");
        expected.add("2");
        expected.add("3");
        expected.add("4");
        expected.add("DONE");

        interpreter.compile(fetchStreamForFile("goto-loop.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(expected.size(), actual.size(), "Actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    public void testGotoPerform() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("A");
        expected.add("B");
        expected.add("B");
        expected.add("C");
        expected.add("D");
        expected.add("E");
        expected.add("C");
        expected.add("D");
        expected.add("E");
        expected.add("F");

        interpreter.compile(fetchStreamForFile("goto-perform.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();
        assertEquals(expected.size(), actual.size(), "Actual size differs from expected size!");

        testEquivalence(expected, actual);
    }


    @Test
    public void testGotoComputed() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("Testing...");
        expected.add("Reached Computed GOTO");

        interpreter.compile(fetchStreamForFile("goto-computed.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(expected.size(), actual.size(), "Actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    public void testSignal() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("MAIN");
        expected.add("We have had an error occur");
        expected.add("MAIN");
        String expectedError = "line: 2, message: Error in the signal paragraph, exiting program";

        try {
            interpreter.compile(fetchStreamForFile("signal.baby"), programOutput);


            fail("We should not get to this part of the test, expected an error to occur!");
        } catch (InterpreterException ie) {
            ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

            assertEquals(expected.size(), actual.size(), "Actual size differs from expected size!");

            testEquivalence(expected, actual);
            assertEquals(ie.getMessage(), expectedError, "We expected an error to occur");
        }
    }

    @Test
    public void testNoSignal() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("MAIN");
        String expectedError = "line: 10, message: Paragraph INVALID-LABEL not found";

        try {
            interpreter.compile(fetchStreamForFile("no-signal.baby"), programOutput);


            fail("We should not get to this part of the test, expected an error to occur!");
        } catch (InterpreterException ie) {
            ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

            assertEquals(expected.size(), actual.size(), "Actual size differs from expected size!");

            testEquivalence(expected, actual);
            assertEquals(ie.getMessage(), expectedError, "We expected an error to occur");
        }
    }

    @Test
    public void testSignalBeforeParagraph() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("Had an ERROR");

        interpreter.compile(fetchStreamForFile("signal-before-paragraphs.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(expected.size(), actual.size(), "Actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    public void testDoubleSignal() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("MAIN");
        expected.add("We have had an error occur");
        expected.add("MAIN");
        expected.add("We are really falling back now");
        expected.add("MAIN");
        String expectedError = "line: 2, message: Error in the signal paragraph, exiting program";

        try {
            interpreter.compile(fetchStreamForFile("double-signal.baby"), programOutput);


            fail("We should not get to this part of the test, expected an error to occur!");
        } catch (InterpreterException ie) {
            ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

            assertEquals(expected.size(), actual.size(), "Actual size differs from expected size!");

            testEquivalence(expected, actual);
            assertEquals(ie.getMessage(), expectedError, "We expected an error to occur");
        }
    }

    @Test
    public void testPicture() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("000");
        expected.add(StringUtils.repeat((char) 0, 3));
        expected.add("013");
        expected.add("510");
        String expectedError = "Cannot multiply an identifier with picture different than 9";

        try {
            interpreter.compile(fetchStreamForFile("picture.baby"), programOutput);
            fail("We should not get this part of the test!");
        } catch (InterpreterException e) {
            ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

            assertEquals(actual.size(), expected.size(), "actual size differs from expected size!");

            testEquivalence(expected, actual);
            assertEquals(e.getMessage(), expectedError, "We expected an error to occur");
        }
    }

    @Test
    @Disabled
    public void testAccept() throws IOException {
        fail("Unimplemented");
    }

    @Test
    @Disabled
    public void testSuccifientQualitifaction() throws IOException {
        fail("Unimplemented");
    }

    @Test
    public void testAlter() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("But you should be here!");

        interpreter.compile(fetchStreamForFile("alter.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(actual.size(), expected.size(), "actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    @Disabled
    public void testCopy() throws IOException {
        fail("Unimplemented");
    }

    @Test
    @Disabled
    public void testIf() throws IOException {
        fail("Unimplemented");
    }

    @Test
    public void testContractedExpressions() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("10");
        expected.add("15");

        interpreter.compile(fetchStreamForFile("contractedBoolean.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(expected.size(), actual.size(), "Actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    public void testContractedExpressionsWithoutComparisonOp() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("A");

        interpreter.compile(fetchStreamForFile("contractedBooleanWithoutComparisonOp.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(expected.size(), actual.size(), "Actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    public void testMove() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("000");
        expected.add("999");
        expected.add("000");
        expected.add("  ");
        expected.add("ÿÿ");
        expected.add(StringUtils.repeat((char) 0, 2));
        expected.add("234");

        interpreter.compile(fetchStreamForFile("move.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(actual.size(), expected.size(), "actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    public void testAlterGoToPerform() throws  IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add(":");
        expected.add("A");
        expected.add("C");
        expected.add(";");
        expected.add("A");
        expected.add("-");

        interpreter.compile(fetchStreamForFile("alter-goto-perform.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(actual.size(), expected.size(), "actual size differs from expected size!");

        testEquivalence(expected, actual);
    }

    @Test
    public void testMovePartTwo() throws IOException {
        ArrayList<String> expected = new ArrayList<>();
        expected.add("23");
        expected.add("67");

        interpreter.compile(fetchStreamForFile("testMovePartTwo.baby"), programOutput);
        ArrayList<Tuple<String, Integer>> actual = programOutput.getCopyOfList();

        assertEquals(actual.size(), expected.size(), "actual size differs from expected size!");

        testEquivalence(expected, actual);
    }
}