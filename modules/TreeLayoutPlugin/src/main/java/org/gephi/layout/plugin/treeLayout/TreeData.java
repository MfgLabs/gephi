package org.gephi.layout.plugin.treeLayout;

import org.gephi.graph.api.Node;
import org.gephi.graph.spi.LayoutData;

/**
 * Created with IntelliJ IDEA.
 * User: jbilcke
 * Date: 3/6/13
 * Time: 4:43 PM
 * To change this template use File | Settings | File Templates.
 */
public class TreeData implements LayoutData {
    //Data
    public float x = 0;
    public float y = 0;

    public int number = 0;
    public Node[] children = {};

    public float change = 0;
    public float shift = 0;
    public float prelim = 0;

    public Node thread = null;
    public Node ancestror = null;
    public float modifier = 0;
    
    public int depth = 0;
    
}
