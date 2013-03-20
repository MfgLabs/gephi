/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gephi.layout.plugin.treeLayout;

import java.util.Arrays;

/**
 *
 * @author jbilcke
 */
public class DataUtils {
        public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static double normalize(double value, double min, double max) {
        return (value - min) / (max - min);
    }
    

}
