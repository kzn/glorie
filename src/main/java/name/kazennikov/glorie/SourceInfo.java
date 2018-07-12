/*
 *  SourceInfo.java
 *
 *  Copyright (c) 1995-2012, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Mark A. Greenwood, 08/12/2010
 *
 */

package name.kazennikov.glorie;

import gate.util.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple class to store and use the mapping between Groovy RHS code and compiled class for error reporting.
 * This is a slightly modified copy of {@link gate.jape.SourceInfo}
 */
public class SourceInfo {

    private List<BlockInfo> blocks = new ArrayList<BlockInfo>();

    private String className = null;

    private String phaseName = null;

    private String sectionName = null;

    public SourceInfo(String phaseName, String sectionName) {
        this.phaseName = phaseName;
        this.sectionName = sectionName;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String addBlock(String previousCode, String codeBlock) {
        if(!codeBlock.startsWith("// Source:")) {
            return codeBlock;
        }

        String info = codeBlock.substring(10, codeBlock.indexOf("\n")).trim();
        String code = codeBlock.substring(codeBlock.indexOf("\n") + 1);

        String grammarURL = info.substring(0, info.lastIndexOf(":"));
        int lineNumber = Integer.parseInt(info.substring(info.lastIndexOf(":") + 1));

        int startLine = previousCode.split("\n").length + 1;
        int endLine = startLine + code.split("\n").length;

        int startOffset = previousCode.length();
        int endOffset = previousCode.length() + code.length();

        blocks.add(new BlockInfo(grammarURL, lineNumber, startLine, endLine, startOffset, endOffset));

        return code;
    }

    public String getSource(String source, int javaLineNumber) {
        for(BlockInfo info : blocks) {
            if(info.contains(javaLineNumber)) {
                return info.getSource(source, info.getGrammarSourceLine(javaLineNumber));
            }
        }

        return "";
    }

    public StackTraceElement getStackTraceElement(int javaLineNumber) {
        for(BlockInfo info : blocks) {
            StackTraceElement grammarSTE = info.getStackTraceElement(javaLineNumber);

            if(grammarSTE != null) {
                return grammarSTE;
            }
        }

        return null;
    }

    /**
     * Enhances a Throwable by replacing mentions of Java code inside a
     * Jape RhsAction with a reference to the original Jape source where
     * available.
     *
     * @param t the Throwable to enhance with Jape source information
     */
    public void enhanceTheThrowable(Throwable t) {
        if(t.getCause() != null) {
            enhanceTheThrowable(t.getCause());
        }

        List<StackTraceElement> stack = new ArrayList<>();

        for(StackTraceElement ste : t.getStackTrace()) {
            if(ste.getClassName().equals(className)) {

                StackTraceElement grammarSTE = null;

                if(ste.getLineNumber() >= 0) {
                    for(BlockInfo info : blocks) {
                        grammarSTE = info.getStackTraceElement(ste.getLineNumber());

                        if(grammarSTE != null)
                            break;
                    }
                }

                stack.add(grammarSTE != null ? grammarSTE : ste);
            } else {
                stack.add(ste);
            }
        }

        t.setStackTrace(stack.toArray(new StackTraceElement[stack.size()]));
    }

    private class BlockInfo {
        String grammarURL;

        int japeLine;

        int startLine;
        int endLine;

        int startOffset;
        int endOffset;

        BlockInfo(String grammarURL, int japeLine, int startLine, int endLine, int startOffset, int endOffset) {
            this.grammarURL = grammarURL;
            this.japeLine = japeLine;
            this.startLine = startLine;
            this.endLine = endLine;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        public boolean contains(int lineNumber) {
            return (startLine <= lineNumber && lineNumber <= endLine);
        }

        @SuppressWarnings("unused")
        public String getNumberedSource(String source) {
            return Strings.addLineNumbers(getSource(source), japeLine);
        }

        public String getSource(String source, int line) {
            String[] lines = getSource(source).split("\n");

            return lines[line - japeLine];
        }

        public int getGrammarSourceLine(int javaLineNumber) {
            if(!contains(javaLineNumber)) {
                return -1;
            }

            return japeLine + (javaLineNumber - startLine);
        }

        public StackTraceElement getStackTraceElement(int javaLineNumber) {
            int lineNumber = getGrammarSourceLine(javaLineNumber);

            if(lineNumber == -1) {
                return null;
            }

            return new StackTraceElement(phaseName, sectionName, grammarURL, lineNumber);
        }

        public String getSource(String source) {
            return source.substring(startOffset, endOffset);
        }
    }

    public String getClassName() {
        return className;
    }
}
