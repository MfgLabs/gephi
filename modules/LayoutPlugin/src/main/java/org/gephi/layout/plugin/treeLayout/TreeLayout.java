/*
Copyright 2013 MFG Labs
Authors : Julian Bilcke, Joachim de Lezardière
Website : http://www.mfglabs.com

This file is part of Gephi.

DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.

Copyright 2011 Gephi Consortium. All rights reserved.

The contents of this file are subject to the terms of either the GNU
General Public License Version 3 only ("GPL") or the Common
Development and Distribution License("CDDL") (collectively, the
"License"). You may not use this file except in compliance with the
License. You can obtain a copy of the License at
http://gephi.org/about/legal/license-notice/
or /cddl-1.0.txt and /gpl-3.0.txt. See the License for the
specific language governing permissions and limitations under the
License.  When distributing the software, include this License Header
Notice in each file and include the License files at
/cddl-1.0.txt and /gpl-3.0.txt. If applicable, add the following below the
License Header, with the fields enclosed by brackets [] replaced by
your own identifying information:
"Portions Copyrighted [year] [name of copyright owner]"

If you wish your version of this file to be governed by only the CDDL
or only the GPL Version 3, indicate your decision by adding
"[Contributor] elects to include this software in this distribution
under the [CDDL or GPL Version 3] license." If you do not indicate a
single choice of license, a recipient has the option to distribute
your version of this file under either the CDDL, the GPL Version 3 or
to extend the choice of license to its licensees as provided above.
However, if you add GPL Version 3 code and therefore, elected the GPL
Version 3 license, then the option applies only if the new code is
made subject to such option by the copyright holder.

Contributor(s):

Portions Copyrighted 2011 Gephi Consortium.
*/
package org.gephi.layout.plugin.treeLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.HierarchicalGraph;
import org.gephi.graph.api.Node;
import org.gephi.graph.api.NodeData;
import org.gephi.layout.plugin.AbstractLayout;
import org.gephi.layout.spi.Layout;
import org.gephi.layout.spi.LayoutBuilder;
import org.gephi.layout.spi.LayoutProperty;
import org.openide.util.NbBundle;

/**
 * Ported from:
 * https://bitbucket.org/JonathanGiles/prefusefx/src/prefuse/action/layout/graph/NodeLinkTreeLayout.java
 * http://read.pudn.com/downloads76/sourcecode/java/288603/%E4%BB%A3%E7%A0%81/graphdrawing/Walker.java__.htm
 * @author Juilan Bilcke
 */
public class TreeLayout extends AbstractLayout implements Layout {

    private static final float SPEED_DIVISOR = 800;
    private static final float AREA_MULTIPLICATOR = 10000;
    //Graph
    protected HierarchicalGraph graph;
    //Properties
    private float area;
    private double gravity;
    private double speed;

    private String rootId; // ID of the root node (could be computed using a walk)

    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
    public TreeLayout(LayoutBuilder layoutBuilder) {
        super(layoutBuilder);
    }

    public void resetPropertiesValues() {
        speed = 1;
        area = 10000;
        gravity = 10;
    }

    public void initAlgo() {
        this.graph = graphModel.getHierarchicalGraphVisible();
        graph.readLock();

        for (Node n : graph.getNodes()) {
            n.getNodeData().setLayoutData(new HierarchicalTreeNodeLayoutData());
        }

        Edge[] edges = graph.getEdgesAndMetaEdges().toArray();
        for (Edge E : edges) {

            Node source = E.getSource();
            Node target = E.getTarget();

            HierarchicalTreeNodeLayoutData sourceLayoutData = target.getNodeData().getLayoutData();
            HierarchicalTreeNodeLayoutData targetLayoutData = target.getNodeData().getLayoutData();

            // set the parent (if the graph is NOT an ordered tree, this will overwrite)
            if (targetLayoutData.parent != null) {
                System.out.println("Warning: there is more than one parent, the graph is NOT an ordered tree (aka plane tree)!");
            }
            targetLayoutData.parent = source;

            Node[] newChild = { target };
            sourceLayoutData.children = concat(sourceLayoutData.children, newChild);

        }
        graph.readUnlock();
    }

    public void goAlgo() {
        this.graph = graphModel.getHierarchicalGraphVisible();
        graph.readLock();
        Node[] nodes = graph.getNodes().toArray();
        Edge[] edges = graph.getEdgesAndMetaEdges().toArray();

        for (Node n : nodes) {
            if (n.getNodeData().getLayoutData() == null || !(n.getNodeData().getLayoutData() instanceof HierarchicalTreeNodeLayoutData)) {
                n.getNodeData().setLayoutData(new HierarchicalTreeNodeLayoutData());
            }
            HierarchicalTreeNodeLayoutData layoutData = n.getNodeData().getLayoutData();
            layoutData.dx = 0;
            layoutData.dy = 0;
        }

        float maxDisplace = (float) (Math.sqrt(AREA_MULTIPLICATOR * area) / 10f);					// Déplacement limite : on peut le calibrer...
        float k = (float) Math.sqrt((AREA_MULTIPLICATOR * area) / (1f + nodes.length));		// La variable k, l'idée principale du layout.

        for (Node N1 : nodes) {
            for (Node N2 : nodes) {	// On fait toutes les paires de noeuds
                if (N1 != N2) {
                    float xDist = N1.getNodeData().x() - N2.getNodeData().x();	// distance en x entre les deux noeuds
                    float yDist = N1.getNodeData().y() - N2.getNodeData().y();
                    float dist = (float) Math.sqrt(xDist * xDist + yDist * yDist);	// distance tout court

                    if (dist > 0) {
                        float repulsiveF = k * k / dist;			// Force de répulsion
                        HierarchicalTreeNodeLayoutData layoutData = N1.getNodeData().getLayoutData();
                        layoutData.dx += xDist / dist * repulsiveF;		// on l'applique...
                        layoutData.dy += yDist / dist * repulsiveF;
                    }
                }
            }
        }
        for (Edge E : edges) {
            // Idem, pour tous les noeuds on applique la force d'attraction

            Node Nf = E.getSource();
            Node Nt = E.getTarget();

            float xDist = Nf.getNodeData().x() - Nt.getNodeData().x();
            float yDist = Nf.getNodeData().y() - Nt.getNodeData().y();
            float dist = (float) Math.sqrt(xDist * xDist + yDist * yDist);

            float attractiveF = dist * dist / k;

            if (dist > 0) {
                HierarchicalTreeNodeLayoutData sourceLayoutData = Nf.getNodeData().getLayoutData();
                HierarchicalTreeNodeLayoutData targetLayoutData = Nt.getNodeData().getLayoutData();
                sourceLayoutData.dx -= xDist / dist * attractiveF;
                sourceLayoutData.dy -= yDist / dist * attractiveF;
                targetLayoutData.dx += xDist / dist * attractiveF;
                targetLayoutData.dy += yDist / dist * attractiveF;
            }
        }
        // gravity
        for (Node n : nodes) {
            NodeData nodeData = n.getNodeData();
            HierarchicalTreeNodeLayoutData layoutData = nodeData.getLayoutData();
            float d = (float) Math.sqrt(nodeData.x() * nodeData.x() + nodeData.y() * nodeData.y());
            float gf = 0.01f * k * (float) gravity * d;
            layoutData.dx -= gf * nodeData.x() / d;
            layoutData.dy -= gf * nodeData.y() / d;
        }
        // speed
        for (Node n : nodes) {
            HierarchicalTreeNodeLayoutData layoutData = n.getNodeData().getLayoutData();
            layoutData.dx *= speed / SPEED_DIVISOR;
            layoutData.dy *= speed / SPEED_DIVISOR;
        }
        for (Node n : nodes) {
            // Maintenant on applique le déplacement calculé sur les noeuds.
            // nb : le déplacement à chaque passe "instantanné" correspond à la force : c'est une sorte d'accélération.
            HierarchicalTreeNodeLayoutData layoutData = n.getNodeData().getLayoutData();
            float xDist = layoutData.dx;
            float yDist = layoutData.dy;
            float dist = (float) Math.sqrt(layoutData.dx * layoutData.dx + layoutData.dy * layoutData.dy);
            if (dist > 0 && !n.getNodeData().isFixed()) {
                float limitedDist = Math.min(maxDisplace * ((float) speed / SPEED_DIVISOR), dist);
                n.getNodeData().setX(n.getNodeData().x() + xDist / dist * limitedDist);
                n.getNodeData().setY(n.getNodeData().y() + yDist / dist * limitedDist);
            }
        }
        graph.readUnlock();
    }

    private Node nextLeft(Node n) {
        HierarchicalTreeNodeLayoutData layoutData = n.getNodeData().getLayoutData();
        Node c = null;

        /*
        if ( n.isExpanded() ) c = (Node)n.getFirstChild();
        return ( c != null ? c : getParams(n).thread );
        */
        return c;
    }

    private Node nextRight(Node n) {
        HierarchicalTreeNodeLayoutData layoutData = n.getNodeData().getLayoutData();
        Node c = null;
        /*
        if ( n.isExpanded() ) c = (Node)n.getLastChild();
        return ( c != null ? c : getParams(n).thread );
        */
        return c;
    }

    private void moveSubtree(Node wm, Node wp, double shift) {
        HierarchicalTreeNodeLayoutData wmLayoutData = wm.getNodeData().getLayoutData();
        HierarchicalTreeNodeLayoutData wpLayoutData = wp.getNodeData().getLayoutData();
        /*
        Params wmp = getParams(wm);
        Params wpp = getParams(wp);
        double subtrees = wpp.number - wmp.number;
        wpp.change -= shift/subtrees;
        wpp.shift += shift;
        wmp.change += shift/subtrees;
        wpp.prelim += shift;
        wpp.mod += shift;
        */
    }

    private void executeShifts(Node n) {
        HierarchicalTreeNodeLayoutData layoutData = n.getNodeData().getLayoutData();
        /*
        double shift = 0, change = 0;
        for ( Node c = (Node)n.getLastChild();
              c != null; c = (Node)c.getPreviousSibling() )
        {
            Params cp = getParams(c);
            cp.prelim += shift;
            cp.mod += shift;
            change += cp.change;
            shift += cp.shift + change;
        }
        */
    }

    private Node ancestor(Node vim, Node v, Node a) {
        HierarchicalTreeNodeLayoutData vimLayoutData = vim.getNodeData().getLayoutData();
        HierarchicalTreeNodeLayoutData vLayoutData = v.getNodeData().getLayoutData();
        HierarchicalTreeNodeLayoutData aLayoutData = a.getNodeData().getLayoutData();
        /*
        Node p = (Node)v.getParent();
        Params vimp = getParams(vim);
        if ( vimp.ancestor.getParent() == p ) {
            return vimp.ancestor;
        } else {
            return a;
        }
        */
        return null;
    }

    private void secondWalk(Node n, Node p, double m, int depth) {
        HierarchicalTreeNodeLayoutData nLayoutData = n.getNodeData().getLayoutData();
        HierarchicalTreeNodeLayoutData pLayoutData = p.getNodeData().getLayoutData();
        /*
        Params np = getParams(n);
        setBreadth(n, p, np.prelim + m);
        setDepth(n, p, m_depths[depth]);

        if ( n.isExpanded() ) {
            depth += 1;
            for ( Node c = (Node)n.getFirstChild();
                  c != null; c = (Node)c.getNextSibling() )
            {
                secondWalk(c, n, m + np.mod, depth);
            }
        }

        np.clear();
        */
    }
    private Node apportion(Node v, Node a) {
        HierarchicalTreeNodeLayoutData vLayoutData = v.getNodeData().getLayoutData();
        HierarchicalTreeNodeLayoutData aLayoutData = a.getNodeData().getLayoutData();
    /*
        v.getAttributes()
        Node w = v.getPreviousSibling();
        if (w == null) {
            return ancestror;
        }


            Node     vip, vim, vop, vom;
            double   sip, sim, sop, som;

            vip = vop = v;
            vim = w;
            vom = vip.getParent().getFirstChild();

            sip = getParams(vip).mod;
            sop = getParams(vop).mod;
            sim = getParams(vim).mod;
            som = getParams(vom).mod;

            Node nr = nextRight(vim);
            Node nl = nextLeft(vip);
            while ( nr != null && nl != null ) {
                vim = nr;
                vip = nl;
                vom = nextLeft(vom);
                vop = nextRight(vop);
                getParams(vop).ancestor = v;
                double shift = (getParams(vim).prelim + sim) -
                        (getParams(vip).prelim + sip) + spacing(vim,vip,false);
                if ( shift > 0 ) {
                    moveSubtree(getAncestor(vim,v,ancestror), v, shift);
                    sip += shift;
                    sop += shift;
                }
                sim += getParams(vim).mod;
                sip += getParams(vip).mod;
                som += getParams(vom).mod;
                sop += getParams(vop).mod;

                nr = nextRight(vim);
                nl = nextLeft(vip);
            }
            if ( nr != null && nextRight(vop) == null ) {
                Params vopp = getParams(vop);
                vopp.thread = nr;
                vopp.mod += sim - sop;
            }
            if ( nl != null && nextLeft(vom) == null ) {
                Params vomp = getParams(vom);
                vomp.thread = nl;
                vomp.mod += sip - som;
                ancestror = v;
            }
    */
        return a;
    }

    public void endAlgo() {
        for (Node n : graph.getNodes()) {
            n.getNodeData().setLayoutData(null);
        }
    }

    @Override
    public boolean canAlgo() {
        return true;
    }


    public LayoutProperty[] getProperties() {
        List<LayoutProperty> properties = new ArrayList<LayoutProperty>();
        final String TREE_LAYOUT = "Tree Layout";

        try {
            properties.add(LayoutProperty.createProperty(
                    this, Float.class,
                    NbBundle.getMessage(TreeLayout.class, "treeLayout.area.name"),
                    TREE_LAYOUT,
                    "treeLayout.area.name",
                    NbBundle.getMessage(TreeLayout.class, "treeLayout.area.desc"),
                    "getArea", "setArea"));
            properties.add(LayoutProperty.createProperty(
                    this, Double.class,
                    NbBundle.getMessage(TreeLayout.class, "treeLayout.gravity.name"),
                    TREE_LAYOUT,
                    "treeLayout.gravity.name",
                    NbBundle.getMessage(TreeLayout.class, "treeLayout.gravity.desc"),
                    "getGravity", "setGravity"));
            properties.add(LayoutProperty.createProperty(
                    this, Double.class,
                    NbBundle.getMessage(TreeLayout.class, "treeLayout.speed.name"),
                    TREE_LAYOUT,
                    "treeLayout.speed.name",
                    NbBundle.getMessage(TreeLayout.class, "treeLayout.speed.desc"),
                    "getSpeed", "setSpeed"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return properties.toArray(new LayoutProperty[0]);
    }


    public Float getArea() {
        return area;
    }

    public void setArea(Float area) {
        this.area = area;
    }

    /**
     * @return the gravity
     */
    public Double getGravity() {
        return gravity;
    }

    /**
     * @param gravity the gravity to set
     */
    public void setGravity(Double gravity) {
        this.gravity = gravity;
    }

    /**
     * @return the speed
     */
    public Double getSpeed() {
        return speed;
    }

    /**
     * @param speed the speed to set
     */
    public void setSpeed(Double speed) {
        this.speed = speed;
    }
}
