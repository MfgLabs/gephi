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
public class HierarchicalTreeNodeLayoutData implements LayoutData {
    //Data
    public float dx = 0;
    public float dy = 0;
    public float old_dx = 0;
    public float old_dy = 0;
    public float freeze = 0f;

    public Node parent = null;
    public Node[] children = {};

    public long change = 0L;
    public long shift = 0L;
    public long prelim = 0L;

    public Node thread = null;
    public long mod = 0L;



}
