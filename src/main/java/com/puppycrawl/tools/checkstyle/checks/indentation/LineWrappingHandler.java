////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2014  Oliver Burn
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////
package com.puppycrawl.tools.checkstyle.checks.indentation;

import java.util.Collection;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.TreeMap;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * This class checks line-wrapping into definitions and expressions. The
 * line-wrapping indentation should be not less then value of the
 * lineWrappingIndentation parameter.
 *
 * @author maxvetrenko
 *
 */
public class LineWrappingHandler
{

    /**
     * The current instance of <code>IndentationCheck</code> class using this
     * handler. This field used to get access to private fields of
     * IndentationCheck instance.
     */
    private final IndentationCheck mIndentCheck;

    /**
     * Root node for current expression.
     */
    private DetailAST mFirstNode;

    /**
     * Last node for current expression.
     */
    private DetailAST mLastNode;

    /**
     * User's value of line wrapping indentation.
     */
    private int mIndentLevel;

    /**
     * Force strict condition in line wrapping case.
     */
    private boolean mForceStrictCondition;

    /**
     * Sets values of class field, finds last node and calculates indentation level.
     *
     * @param aInstance
     *            instance of IndentationCheck.
     * @param aFirstNode
     *            root node for current expression..
     */
    public LineWrappingHandler(IndentationCheck aInstance, DetailAST aFirstNode)
    {
        mIndentCheck = aInstance;
        mFirstNode = aFirstNode;
        mLastNode = findLastNode(mFirstNode);
        mIndentLevel = mIndentCheck.getLineWrappingIndentation();
        mForceStrictCondition = mIndentCheck.getForceStrictCondition();
    }

    /**
     * Finds last node of AST subtree.
     *
     * @param aFirstNode the first node of expression or definition.
     * @return last node.
     */
    public DetailAST findLastNode(DetailAST aFirstNode)
    {
        return aFirstNode.getLastChild().getPreviousSibling();
    }

    /**
     * @return correct indentation for current expression.
     */
    public int getCurrentIndentation()
    {
        return mFirstNode.getColumnNo() + mIndentLevel;
    }

    // Getters for private fields.

    public final DetailAST getFirstNode()
    {
        return mFirstNode;
    }

    public final DetailAST getLastNode()
    {
        return mLastNode;
    }

    public final int getIndentLevel()
    {
        return mIndentLevel;
    }

    /**
     * Checks line wrapping into expressions and definitions.
     */
    public void checkIndentation()
    {
        final NavigableMap<Integer, DetailAST> firstNodesOnLines = collectFirstNodes();

        final DetailAST firstNode = firstNodesOnLines.get(firstNodesOnLines.firstKey());
        if (firstNode.getType() == TokenTypes.AT) {
            checkAnnotationIndentation(firstNode, firstNodesOnLines);
        }

        // First node should be removed because it was already checked before.
        firstNodesOnLines.remove(firstNodesOnLines.firstKey());
        final int firstNodeIndent = getFirstNodeIndent(firstNode);
        final int currentIndent = firstNodeIndent + mIndentLevel;

        for (DetailAST node : firstNodesOnLines.values()) {
            final int currentType = node.getType();

            if (currentType == TokenTypes.RCURLY
                    || currentType == TokenTypes.RPAREN
                    || currentType == TokenTypes.ARRAY_INIT)
            {
                logWarningMessage(node, firstNodeIndent);
            }
            else if (currentType == TokenTypes.LITERAL_IF) {
                final DetailAST parent = node.getParent();

                if (parent.getType() == TokenTypes.LITERAL_ELSE) {
                    logWarningMessage(parent, currentIndent);
                }
            }
            else {
                logWarningMessage(node, currentIndent);
            }
        }
    }

    /**
     * Calculates indentation of first node.
     *
     * @param aNode
     *            first node.
     * @return indentation of first node.
     */
    private int getFirstNodeIndent(DetailAST aNode)
    {
        int indentLevel = aNode.getColumnNo();

        if (aNode.getType() == TokenTypes.LITERAL_IF
                && aNode.getParent().getType() == TokenTypes.LITERAL_ELSE)
        {
            final DetailAST lcurly = aNode.getParent().getPreviousSibling();
            final DetailAST rcurly = lcurly.getLastChild();

            if (lcurly.getType() == TokenTypes.SLIST
                    && rcurly.getLineNo() == aNode.getLineNo())
            {
                indentLevel = rcurly.getColumnNo();
            }
            else {
                indentLevel = aNode.getParent().getColumnNo();
            }
        }
        return indentLevel;
    }

    /**
     * Finds first nodes on line and puts them into Map.
     *
     * @return NavigableMap which contains lines numbers as a key and first
     *         nodes on lines as a values.
     */
    private NavigableMap<Integer, DetailAST> collectFirstNodes()
    {
        final NavigableMap<Integer, DetailAST> result = new TreeMap<Integer, DetailAST>();

        result.put(mFirstNode.getLineNo(), mFirstNode);
        DetailAST curNode = mFirstNode.getFirstChild();

        while (curNode != null && curNode != mLastNode) {

            if (curNode.getType() == TokenTypes.OBJBLOCK) {
                curNode = curNode.getNextSibling();
            }

            if (curNode != null) {
                final DetailAST firstTokenOnLine = result.get(curNode.getLineNo());

                if (firstTokenOnLine == null
                        || firstTokenOnLine != null
                        && firstTokenOnLine.getColumnNo() >= curNode.getColumnNo())
                {
                    result.put(curNode.getLineNo(), curNode);
                }
                curNode = getNextCurNode(curNode);
            }
        }
        return result;
    }

    /**
     * Returns next curNode node.
     *
     * @param aCurNode current node.
     * @return next curNode node.
     */
    private DetailAST getNextCurNode(DetailAST aCurNode)
    {
        DetailAST nodeToVisit = aCurNode.getFirstChild();
        DetailAST currentNode = aCurNode;

        while ((currentNode != null) && (nodeToVisit == null)) {
            nodeToVisit = currentNode.getNextSibling();
            if (nodeToVisit == null) {
                currentNode = currentNode.getParent();
            }
        }
        return nodeToVisit;
    }

    /**
     * Checks line wrapping into annotations.
     *
     * @param aAtNode at-clause node.
     * @param aFirstNodesOnLines map which contains
     *     first nodes as values and line numbers as keys.
     */
    private void checkAnnotationIndentation(DetailAST aAtNode,
            NavigableMap<Integer, DetailAST> aFirstNodesOnLines)
    {
        final int currentIndent = aAtNode.getColumnNo() + mIndentLevel;
        final int firstNodeIndent = aAtNode.getColumnNo();
        final Collection<DetailAST> values = aFirstNodesOnLines.values();
        final DetailAST lastAnnotationNode = getLastAnnotationNode(aAtNode);
        final int lastAnnotationLine = lastAnnotationNode.getLineNo();
        final int lastAnnotattionColumn = lastAnnotationNode.getColumnNo();

        final Iterator<DetailAST> itr = values.iterator();
        while (itr.hasNext() && aFirstNodesOnLines.size() > 1) {
            final DetailAST node = itr.next();

            if (node.getLineNo() < lastAnnotationLine
                    || node.getLineNo() == lastAnnotationLine
                    && node.getColumnNo() <= lastAnnotattionColumn)
            {
                final DetailAST parentNode = node.getParent();
                if (node.getType() == TokenTypes.AT
                        && parentNode.getParent().getType() == TokenTypes.MODIFIERS)
                {
                    logWarningMessage(node, firstNodeIndent);
                }
                else {
                    logWarningMessage(node, currentIndent);
                }
                itr.remove();
            }
            else {
                break;
            }
        }
    }

    /**
     * Finds and returns last annotation node.
     * @param aAtNode first at-clause node.
     * @return last annotation node.
     */
    private DetailAST getLastAnnotationNode(DetailAST aAtNode)
    {
        DetailAST lastAnnotation = aAtNode.getParent();
        while (lastAnnotation.getNextSibling() != null
                && lastAnnotation.getNextSibling().getType() == TokenTypes.ANNOTATION)
        {
            lastAnnotation = lastAnnotation.getNextSibling();
        }
        return lastAnnotation.getLastChild();
    }

    /**
     * Logs warning message if indentation is incorrect.
     *
     * @param aCurrentNode
     *            current node which probably invoked an error.
     * @param aCurrentIndent
     *            correct indentation.
     */
    private void logWarningMessage(DetailAST aCurrentNode, int aCurrentIndent)
    {
        if (mForceStrictCondition) {
            if (aCurrentNode.getColumnNo() != aCurrentIndent) {
                mIndentCheck.indentationLog(aCurrentNode.getLineNo(),
                        "indentation.error", aCurrentNode.getText(),
                        aCurrentNode.getColumnNo(), aCurrentIndent);
            }
        }
        else {
            if (aCurrentNode.getColumnNo() < aCurrentIndent) {
                mIndentCheck.indentationLog(aCurrentNode.getLineNo(),
                        "indentation.error", aCurrentNode.getText(),
                        aCurrentNode.getColumnNo(), aCurrentIndent);
            }
        }
    }
}
