/* Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.mahout.matrix;

import java.util.HashMap;
import java.util.Map;
import java.io.DataOutput;
import java.io.IOException;
import java.io.DataInput;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;

/**
 * Implements vector that only stores non-zero doubles
 *
 */
public class SparseVector extends AbstractVector {

  /** For serialization purposes only. */
  public SparseVector() {
  }

  private Map<Integer, Double> values;


  private int cardinality;

  /**
   * Decode a new instance from the argument
   * 
   * @param writableComparable
   *            a writableComparable produced by the asWritableComparable method
   * @return a DenseVector
   */
  public static Vector decodeFormat(WritableComparable writableComparable) {
    return decodeFormat(writableComparable.toString());
  }

  /**
   * Decode a new instance from the formatted string
   *
   * @param formattedString
   *            a string produced by the asFormatString method
   * @return a DenseVector
   */
  public static Vector decodeFormat(String formattedString) {
    String[] pts = formattedString.split(",");
    SparseVector result = null;
    for (int i = 0; i < pts.length; i++) {
      String pt = pts[i].trim();
      if (pt.startsWith("[s")) {
        int c = new Integer(pts[i].substring(2));
        result = new SparseVector(c);
      } else if (!pt.startsWith("]")) {
        int ix = pt.indexOf(':');
        Integer index = new Integer(pt.substring(0, ix).trim());
        Double value = new Double(pt.substring(ix + 1));
        result.setQuick(index, value);
      }
    }
    return result;
  }

  public SparseVector(int cardinality) {
    super();
    values = new HashMap<Integer, Double>();
    this.cardinality = cardinality;
  }

  @Override
  protected Matrix matrixLike(int rows, int columns) {
    int[] cardinality = { rows, columns };
    return new SparseRowMatrix(cardinality);
  }

  @Override
  public WritableComparable asWritableComparable() {
    String out = asFormatString();
    return new Text(out);
  }

  @Override
  public String asFormatString() {
    StringBuilder out = new StringBuilder();
    out.append("[s").append(cardinality).append(", ");
    for (Integer index : values.keySet())
      out.append(index).append(':').append(values.get(index)).append(", ");
    out.append("] ");
    return out.toString();
  }

  @Override
  public int cardinality() {
    return cardinality;
  }

  @Override
  public SparseVector copy() {
    SparseVector result = like();
    for (Integer index : values.keySet())
      result.setQuick(index, values.get(index));
    return result;
  }

  @Override
  public double getQuick(int index) {
    Double value = values.get(index);
    if (value == null)
      return 0;
    else
      return value;
  }

  @Override
  public void setQuick(int index, double value) {
    if (value == 0)
      values.remove(index);
    else
      values.put(index, value);
  }

  @Override
  public int size() {
    return values.size();
  }

  @Override
  public double[] toArray() {
    double[] result = new double[cardinality];
    for (int i = 0; i < cardinality; i++)
      result[i] = getQuick(i);
    return result;
  }

  @Override
  public Vector viewPart(int offset, int length) throws CardinalityException,
      IndexException {
    if (length > cardinality)
      throw new CardinalityException();
    if (offset < 0 || offset + length > cardinality)
      throw new IndexException();
    return new VectorView(this, offset, length);
  }

  @Override
  public boolean haveSharedCells(Vector other) {
    if (other instanceof SparseVector)
      return other == this;
    else
      return other.haveSharedCells(this);
  }

  @Override
  public SparseVector like() {
    return new SparseVector(cardinality);
  }

  @Override
  public Vector like(int newCardinality) {
    return new SparseVector(newCardinality);
  }

  @Override
  public java.util.Iterator<Vector.Element> iterator() {
    return new Iterator();
  }


  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SparseVector that = (SparseVector) o;

    if (cardinality != that.cardinality) return false;
    if (values != null ? !values.equals(that.values) : that.values != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (values != null ? values.hashCode() : 0);
    result = 31 * result + cardinality;
    return result;
  }

  private class Iterator implements java.util.Iterator<Vector.Element> {
    private java.util.Iterator<Map.Entry<Integer, Double>> it;

    public Iterator() {
      it = values.entrySet().iterator();
    }

    public boolean hasNext() {
      return it.hasNext();
    }

    public Element next() {
      return new Element(it.next().getKey());
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public double zSum() {
    java.util.Iterator<Double> iter = values.values().iterator();
    double result = 0;
    while (iter.hasNext())
      result += iter.next();
    return result;
  }

  @Override
  public double dot(Vector x) throws CardinalityException {
    if (cardinality() != x.cardinality())
      throw new CardinalityException();
    java.util.Iterator<Integer> iter = values.keySet().iterator();
    double result = 0;
    while (iter.hasNext()) {
      int nextIndex = iter.next();
      result += getQuick(nextIndex) * x.getQuick(nextIndex);
    }
    return result;
  }

  public void write(DataOutput dataOutput) throws IOException {
    dataOutput.writeInt(cardinality());
    dataOutput.writeInt(size());
    for (Vector.Element element : this) {
      if (element.get() != 0d) {
        dataOutput.writeInt(element.index());
        dataOutput.writeDouble(element.get());
      }
    }
  }

  public void readFields(DataInput dataInput) throws IOException {
    int cardinality = dataInput.readInt();
    Map<Integer, Double> values = new HashMap<Integer, Double>();
    int size = dataInput.readInt();
    for (int i = 0; i < size; i++) {
      values.put(dataInput.readInt(), dataInput.readDouble());
    }
    this.cardinality = cardinality;
    this.values = values;
  }

}