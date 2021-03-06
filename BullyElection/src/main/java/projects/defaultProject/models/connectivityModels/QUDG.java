/*
BSD 3-Clause License

Copyright (c) 2007-2013, Distributed Computing Group (DCG)
                         ETH Zurich
                         Switzerland
                         dcg.ethz.ch
              2017-2018, André Brait

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package projects.defaultProject.models.connectivityModels;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import sinalgo.configuration.Configuration;
import sinalgo.exception.CorruptConfigurationEntryException;
import sinalgo.exception.NotYetImplementedException;
import sinalgo.models.ConnectivityModelHelper;
import sinalgo.nodes.Node;
import sinalgo.nodes.Position;
import sinalgo.runtime.Main;
import sinalgo.tools.statistics.Distribution;

import java.util.Random;

/**
 * Implementation of a quasi unit disc graph connectivy model. This connectivity
 * model is close to the UDG, but does not have a sharp bound on the maximum
 * radius. <br>
 * In the QUDG, two nodes are always connected if their distance d is either
 * smaller than a threshold rMin. If their distance is in the range ]rMin,
 * rMax], where rMax is an upper threshold, the edge exists with a given
 * probability p. <br>
 * There are two flavors of QUDG for the case that the distance is in the range
 * ]rMin, rMax]:
 *
 * <br>
 * <b>a)</b> Constant probability: The connection is equally likely for all
 * distances in the range. The configuration entry looks as following:
 *
 * <pre>
 * &lt;QUDG rMin="50" rMax="100" ProbabilityType="constant" connectionProbability="0.6" /&gt;
 * </pre>
 *
 * <br>
 * <b>b)</b> Linearly decreasing probability in the range ]rMin, rMax]. The
 * probability to be connected decreaseslinearly from '1' to '0' in the interval
 * ]rMin, rMax]. The configuration entry looks as following:
 *
 * <pre>
 * &lt;QUDG rMin="50" rMax="100" ProbabilityType="linear" /&gt;
 * </pre>
 *
 * <br>
 * <b>Note:</b> Because the connectivity is updated in every round, this results
 * in a constantly changing connectivty graph, because the edges with length
 * ]rMin, rMax] are randomly added or removed in each round. <br>
 * <br>
 */
public class QUDG extends ConnectivityModelHelper {

    // the lower threshold of the distance between two nodes below they are always
    // connected
    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.PRIVATE)
    private static double r_min_squared;

    // the upper threshold of the distance between two nodes above which to nodes
    // are never connected.
    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.PRIVATE)
    private static double r_max_squared;

    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.PRIVATE)
    private static double m;

    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.PRIVATE)
    private static double q; // for linear probability

    // The probability to add an edge if the distance between two nodes is in the
    // range ]r_min, r_max].
    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.PRIVATE)
    private static double probability;

    // Instance of the framework intern random number generator.
    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.PRIVATE)
    private static Random rand = Distribution.getRandom();

    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.PRIVATE)
    private int probabilityType; // 0 = constant probability, 1 = linear, 2 = quadratic

    /**
     * In the QUDG graph, two nodes are always connected if their mutual distance is
     * below r_min, never connected if their mutual distance is above r_max and
     * connected with a certain probability if their mutual distance is between
     * r_min and r_max.
     *
     * @see sinalgo.models.ConnectivityModelHelper#isConnected(sinalgo.nodes.Node,
     * sinalgo.nodes.Node)
     */
    @Override
    public boolean isConnected(Node from, Node to) {
        Position p1 = from.getPosition();
        Position p2 = to.getPosition();

        double d = p1.squareDistanceTo(p2);
        if (d <= getR_min_squared()) {
            return true; // the two nodes are always connected
        }
        if (d > getR_max_squared()) {
            return false; // the two nodes are never connected
        }
        // the distance between the two nodes is between r_min and r_max. Now, we
        // randomly
        // determine whether the edge exists or not.

        if (this.getProbabilityType() == 1) { // linear probability
            setProbability(Math.sqrt(d) * getM() + getQ());
        } else if (this.getProbabilityType() == 2) { // quadratic probability
            // ... not yet implemented
        }
        return getRand().nextDouble() <= getProbability();
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // -
    // Code to initialize the static variables of this class
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // -

    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.PRIVATE)
    private static boolean initialized; // indicates whether the static fields of this class have already been
    // initialized

    /**
     * The default constructor for this class.
     * <p>
     * The first time this constructor is called, it initializes the static
     * parameters of this class. This approach is necessary as to give proper error
     * reporting if one of the initialization steps fails.
     *
     * @throws CorruptConfigurationEntryException If one of the initialization steps fails.
     */
    public QUDG() throws CorruptConfigurationEntryException {
        // only call the first time a QUDG object is created
        if (!isInitialized()) {
            // speed up by comparing the squared distances (needs not take the square root
            // to get the distance)
            double r_min = Configuration.getDoubleParameter("QUDG/rMin");
            setR_min_squared(r_min * r_min);

            double r_max = Configuration.getDoubleParameter("QUDG/rMax");
            setR_max_squared(r_max * r_max);

            // Sanity check
            double geomNodeRMax = Configuration.getDoubleParameter("GeometricNodeCollection/rMax");
            if (r_max > geomNodeRMax) { // dangerous! This is probably not what the user wants!
                Main.minorError(
                        "WARNING: The maximum transmission range used for the QUDG connectivity model is larger than the maximum transmission range specified for the GeometricNodeCollection.\nAs a result, not all connections will be found! Either fix the problem in the project-specific configuration file or the '-overwrite' command line argument.");
            }
            if (r_max <= r_min) {
                Main.minorError(
                        "WARNING: The maximum transmission range used for the QUDG connectivity model is not larger than the minimum tansmission range.\nEither fix the problem in the project-specific configuration file or the '-overwrite' command line argument.");
            }

            // TODO: rewrite the docu of this class
            String type = Configuration.getStringParameter("QUDG/ProbabilityType");
            switch (type.toLowerCase()) {
                case "constant":
                    this.setProbabilityType(0);
                    setProbability(Configuration.getDoubleParameter("QUDG/connectionProbability"));
                    break;
                case "linear":
                    this.setProbabilityType(1);
                    setM(1 / (r_min - r_max));
                    setQ(r_max / (r_max - r_min));
                    break;
                case "quadratic":
                    this.setProbabilityType(2);
                    throw new NotYetImplementedException("QUDG does not yet support quadratic probability distributions.");
                default:
                    // TODO: rewrite the following exception, rewrite docu as well
                    throw new CorruptConfigurationEntryException(
                            "The QUDG connectivity model requires an entry in the project"
                                    + " configuration file that specifies the kind of probability to be applied if the distance between two nodes "
                                    + "lies between rMin and rMax. Possible values for ProbabilityType are 'constant', 'linear', and 'quadratic'.\n\n"
                                    + "'constant' requires yet another entry 'connectionProbability', which specifies the constant probability at which the connection exists.\n\n"
                                    + "'linear' applies a linear regression that decreases from 1 to 0 from rMin to rMax.\n\n"
                                    + "'quadratic' applies a quadratic regression that decreases from 1 to 0 from rMin to rMax.\n\n");
            }

            setProbability(Configuration.getDoubleParameter("QUDG/connectionProbability"));
            setInitialized(true);
        }
    }

}
