package io.hyperfoil.tools.horreum.algos;

import static java.lang.Math.*;

public class EDivisive {
/*

    private static final double MACHEP = 1.11022302462515654042E-16;
    private static final double MAXLOG = 7.09782712893383996732E2;
    private static final double MINLOG = -7.45133219101941108420E2;
    private static final double MAXGAM = 171.624376956302725;
    private static final double MAXLGM = 2.556348e305;
    private static final double ASYMP_FACTOR = 1000.0;

    private static final double big = 4.503599627370496e15;
    private static final double biginv = 2.22044604925031308085e-16;

    private static final double LOGPI = 1.14472988584940017414;
    private static final double LS2PI = 0.91893853320467274178;

    */
/* A[]: Stirling's formula expansion of log Gamma
     * B[], C[]: log Gamma function between 2 and 3
     *//*

    private static final double A[] = {
            8.11614167470508450300E-4,
            -5.95061904284301438324E-4,
            7.93650340457716943945E-4,
            -2.77777777730099687205E-3,
            8.33333333333331927722E-2
    };

    private static final double B[] = {
            -1.37825152569120859100E3,
            -3.88016315134637840924E4,
            -3.31612992738871184744E5,
            -1.16237097492762307383E6,
            -1.72173700820839662146E6,
            -8.53555664245765465627E5
    };

    private static final double C[] = {
            */
/* 1.00000000000000000000E0, *//*

            -3.51815701436523470549E2,
            -1.70642106651881159223E4,
            -2.20528590553854454839E5,
            -1.13933444367982507207E6,
            -2.53252307177582951285E6,
            -2.01889141433532773231E6
    };


    public static Object _ttest_ind_from_stats(Double mean1, Double mean2, Double denom, int df, String alternative) {

        Double d = mean1 - mean2;
//        with np.errstate(divide = 'ignore', invalid = 'ignore'):
        Double t = d / denom;
        TTestFInishResult finishResult = _ttest_finish(df, t, alternative);

        return finishResult;
    }

    */
/*
    *             """Common code between all 3 t-test functions."""
                # We use ``stdtr`` directly here as it handles the case when ``nan``
                # values are present in the data and masked arrays are passed
        # while ``t.cdf`` emits runtime warnings. This way ``_ttest_finish``
                # can be shared between the ``stats`` and ``mstats`` versions.
    *//*

    public static TTestFInishResult _ttest_finish(int df, Double t, String alternative) {

        Double pval;

        switch (alternative) {
            case "less":
                pval = stdtr(df, t);
            case "greater":
                pval = stdtr(df, -t);
            case "two-sided":
                pval = stdtr(df, -abs(t)) * 2;
            default:
//            raise ValueError("alternative must be " "'less', 'greater' or 'two-sided'")
                throw new RuntimeException("alternative must be 'less', 'greater' or 'two-sided'");

        }

        if t.ndim == 0:
        t = t[()]
        if pval.ndim == 0:
        pval = pval[()]

        return new TTestFInishResult(t,pval);

    }

    public static Double stdtr(int k, double t) {
        double x, rk, z, f, tz, p, xsqk;
        int j;

        if (k <= 0) {
//            sf_error("stdtr", SF_ERROR_DOMAIN, NULL);
            return (null);
        }

        if (t == 0)
            return (0.5);

        if (t < -2.0) {
            rk = k;
            z = rk / (rk + t * t);
            p = 0.5 * incbet(0.5 * rk, 0.5, z);
            return (p);
        }

        */
/*     compute integral from -t to + t *//*

        if (t < 0)
            x = -t;
        else
            x = t;

        rk = k;            */
/* degrees of freedom *//*

        z = 1.0 + (x * x) / rk;

        */
/* test if k is odd or even *//*

        if ((k & 1) != 0) {
            */
/*      computation for odd k   *//*

            xsqk = x / sqrt(rk);
            p = atan(xsqk);
            if (k > 1) {
                f = 1.0;
                tz = 1.0;
                j = 3;
                while ((j <= (k - 2)) && ((tz / f) > MACHEP)) {
                    tz *= (j - 1) / (z * j);
                    f += tz;
                    j += 2;
                }
                p += f * xsqk / z;
            }
            p *= 2.0 / PI;
        } else {

            */
/*      computation for even k  *//*


            f = 1.0;
            tz = 1.0;
            j = 2;

            while ((j <= (k - 2)) && ((tz / f) > MACHEP)) {
                tz *= (j - 1) / (z * j);
                f += tz;
                j += 2;
            }
            p = f * x / sqrt(z * rk);
        }

        */
/*     common exit     *//*



        if (t < 0)
            p = -p;            */
/* note destruction of relative accuracy *//*


        p = 0.5 + 0.5 * p;
        return (p);
    }


    public static Double incbet(double aa, double bb, double xx) {
        double a, b, t, x, xc, w, y;
        int flag;

        if (aa <= 0.0 || bb <= 0.0) {
            //sf_error("incbet", SF_ERROR_DOMAIN, NULL);
            return (null);
        }

        if ((xx <= 0.0) || (xx >= 1.0)) {
            if (xx == 0.0)
                return (0.0);
            if (xx == 1.0)
                return (1.0);
            domerr:
            //sf_error("incbet", SF_ERROR_DOMAIN, NULL);
            return (null);
        }

        done:
        {
            flag = 0;
            if ((bb * xx) <= 1.0 && xx <= 0.95) {
                t = pseries(aa, bb, xx);
                break done;
            }

            w = 1.0 - xx;

            */
/* Reverse a and b if x is greater than the mean. *//*

            if (xx > (aa / (aa + bb))) {
                flag = 1;
                a = bb;
                b = aa;
                xc = xx;
                x = w;
            } else {
                a = aa;
                b = bb;
                xc = w;
                x = xx;
            }

            if (flag == 1 && (b * x) <= 1.0 && x <= 0.95) {
                t = pseries(a, b, x);
                break done;
            }

            */
/* Choose expansion for better convergence. *//*

            y = x * (a + b - 2.0) - (a - 1.0);
            if (y < 0.0)
                w = incbcf(a, b, x);
            else
                w = incbd(a, b, x) / xc;

            */
/* Multiply w by the factor
             * a      b   _             _     _
             * x  (1-x)   | (a+b) / ( a | (a) | (b) ) .   *//*


            y = a * log(x);
            t = b * log(xc);
            if ((a + b) < MAXGAM && abs(y) < MAXLOG && abs(t) < MAXLOG) {
                t = pow(xc, b);
                t *= pow(x, a);
                t /= a;
                t *= w;
                t *= 1.0 / beta(a, b);
                break done;
            }
            */
/* Resort to logarithms.  *//*

            y += t - lbeta(a, b);
            y += log(w / a);
            if (y < MINLOG)
                t = 0.0;
            else
                t = exp(y);
        }

        if (flag == 1) {
            if (t <= MACHEP)
                t = 1.0 - MACHEP;
            else
                t = 1.0 - t;
        }
        return (t);
    }


    static double pseries(double a, double b, double x) {
        double s, t, u, v, n, t1, z, ai;

        ai = 1.0 / a;
        u = (1.0 - b) * x;
        v = u / (a + 1.0);
        t1 = v;
        t = u;
        n = 2.0;
        s = 0.0;
        z = MACHEP * ai;
        while (abs(v) > z) {
            u = (n - b) * x / n;
            t *= u;
            v = t / (a + n);
            s += v;
            n += 1.0;
        }
        s += t1;
        s += ai;

        u = a * log(x);
        if ((a + b) < MAXGAM && abs(u) < MAXLOG) {
            t = 1.0 / beta(a, b);
            s = s * t * pow(x, a);
        } else {
            t = -lbeta(a, b) + u + log(s);
            if (t < MINLOG)
                s = 0.0;
            else
                s = exp(t);
        }
        return (s);
    }


    static double beta(double a, double b) {
        double y;
        int sign = 1;

        overflow:
        {
            if (a <= 0.0) {
                if (a == floor(a)) {
                    if (a == (int) a) {
                        return beta_negint((int) a, b);
                    } else {
                        break overflow;
                    }
                }
            }

            if (b <= 0.0) {
                if (b == floor(b)) {
                    if (b == (int) b) {
                        return beta_negint((int) b, a);
                    } else {
                        break overflow;
                    }
                }
            }

            if (abs(a) < abs(b)) {
                y = a;
                a = b;
                b = y;
            }

            if (abs(a) > ASYMP_FACTOR * abs(b) && a > ASYMP_FACTOR) {
                */
/* Avoid loss of precision in lgam(a + b) - lgam(a) *//*

                y = lbeta_asymp(a, b, sign);
                return sign * exp(y);
            }

            y = a + b;
            if (abs(y) > MAXGAM || abs(a) > MAXGAM || abs(b) > MAXGAM) {
                int sgngam;
                y = lgam_sgn(y, sgngam);
                sign *= sgngam;        */
/* keep track of the sign *//*

                y = lgam_sgn(b, sgngam) - y;
                sign *= sgngam;
                y = lgam_sgn(a, sgngam) + y;
                sign *= sgngam;
                if (y > MAXLOG) {
                    break overflow;
                }
                return (sign * exp(y));
            }

            y = Gamma(y);
            a = Gamma(a);
            b = Gamma(b);
            if (y == 0.0)
                break overflow;

            if (abs(abs(a) - abs(y)) > abs(abs(b) - abs(y))) {
                y = b / y;
                y *= a;
            } else {
                y = a / y;
                y *= b;
            }

            return (y);
        }

//        sf_error("beta", SF_ERROR_OVERFLOW, NULL);
        return (sign * Double.POSITIVE_INFINITY);
    }


    static double Gamma(double x) {
        double p, q, z;
        int i;
        int sgngam = 1;

        if (!cephes_isfinite(x)) {
            return x;
        }
        q = abs(x);

        if (q > 33.0) {
            if (x < 0.0) {
                p = floor(q);
                if (p == q) {
                    gamnan:
                    sf_error("Gamma", SF_ERROR_OVERFLOW, NULL);
                    return (INFINITY);
                }
                i = p;
                if ((i & 1) == 0)
                    sgngam = -1;
                z = q - p;
                if (z > 0.5) {
                    p += 1.0;
                    z = q - p;
                }
                z = q * sin(M_PI * z);
                if (z == 0.0) {
                    return (sgngam * INFINITY);
                }
                z = abs(z);
                z = M_PI / (z * stirf(q));
            } else {
                z = stirf(x);
            }
            return (sgngam * z);
        }

        z = 1.0;
        while (x >= 3.0) {
            x -= 1.0;
            z *= x;
        }

        while (x < 0.0) {
            if (x > -1.E-9)
	    goto small;
            z /= x;
            x += 1.0;
        }

        while (x < 2.0) {
            if (x < 1.e-9)
	    goto small;
            z /= x;
            x += 1.0;
        }

        if (x == 2.0)
            return (z);

        x -= 2.0;
        p = polevl(x, P, 6);
        q = polevl(x, Q, 7);
        return (z * p / q);

        small:
        if (x == 0.0) {
	goto gamnan;
        } else
            return (z / ((1.0 + 0.5772156649015329 * x) * x));
    }


    */
/*
     * Special case for a negative integer argument
     *//*


    static double beta_negint(int a, double b) {
        int sgn;
        if (b == (int) b && 1 - a - b > 0) {
            sgn = ((int) b % 2 == 0) ? 1 : -1;
            return sgn * beta(1 - a - b, b);
        } else {
//            sf_error("lbeta", SF_ERROR_OVERFLOW, NULL);
            return Double.POSITIVE_INFINITY;
        }
    }

    static double lbeta_negint(int a, double b) {
        double r;
        if (b == (int) b && 1 - a - b > 0) {
            r = lbeta(1 - a - b, b);
            return r;
        } else {
            //sf_error("lbeta", SF_ERROR_OVERFLOW, NULL);
            return Double.POSITIVE_INFINITY;
        }
    }

    */
/* Natural log of |beta|. *//*


    static double lbeta(double a, double b) {
        double y;
        int sign;

        sign = 1;

        if (a <= 0.0) {
            if (a == floor(a)) {
                if (a == (int) a) {
                    return lbeta_negint((int) a, b);
                } else {
                goto over;
                }
            }
        }

        if (b <= 0.0) {
            if (b == floor(b)) {
                if (b == (int) b) {
                    return lbeta_negint((int) b, a);
                } else {
                goto over;
                }
            }
        }

        if (abs(a) < abs(b)) {
            y = a;
            a = b;
            b = y;
        }

        if (abs(a) > ASYMP_FACTOR * abs(b) && a > ASYMP_FACTOR) {
            */
/* Avoid loss of precision in lgam(a + b) - lgam(a) *//*

            y = lbeta_asymp(a, b, sign);
            return y;
        }

        y = a + b;
        if (abs(y) > MAXGAM || abs(a) > MAXGAM || abs(b) > MAXGAM) {
            int sgngam;
            y = lgam_sgn(y, sgngam);
            sign *= sgngam;        */
/* keep track of the sign *//*

            y = lgam_sgn(b, sgngam) - y;
            sign *= sgngam;
            y = lgam_sgn(a, sgngam) + y;
            sign *= sgngam;
            return (y);
        }

        y = Gamma(y);
        a = Gamma(a);
        b = Gamma(b);
        if (y == 0.0) {
            over:
            //sf_error("lbeta", SF_ERROR_OVERFLOW, NULL);
            return (sign * Double.POSITIVE_INFINITY);
        }

        if (abs(abs(a) - abs(y)) > abs(abs(b) - abs(y))) {
            y = b / y;
            y *= a;
        } else {
            y = a / y;
            y *= b;
        }

        if (y < 0) {
            y = -y;
        }

        return (log(y));
    }


    */
/*
     * Asymptotic expansion for  ln(|B(a, b)|) for a > ASYMP_FACTOR*max(|b|, 1).
     *//*

    static double lbeta_asymp(double a, double b, int sgn) {
        double r = lgam_sgn(b, sgn);
        r -= b * log(a);

        r += b * (1 - b) / (2 * a);
        r += b * (1 - b) * (1 - 2 * b) / (12 * a * a);
        r += -b * b * (1 - b) * (1 - b) / (12 * a * a * a);

        return r;
    }

    static double lgam_sgn(double x, int sign) {
        double p, q, u, w, z;
        int i;

        sign = 1;

        if (!cephes_isfinite(x))
            return x;

        if (x < -34.0) {
            q = -x;
            w = lgam_sgn(q, sign);
            p = floor(q);
            if (p == q) {
                lgsing:
                //sf_error("lgam", SF_ERROR_SINGULAR, NULL);
                return (Double.POSITIVE_INFINITY);
            }
            i = p;
            if ((i & 1) == 0)
	    *sign = -1;
	else
	    *sign = 1;
            z = q - p;
            if (z > 0.5) {
                p += 1.0;
                z = p - q;
            }
            z = q * sin(M_PI * z);
            if (z == 0.0)
	    goto lgsing;
            */
/*     z = log(M_PI) - log( z ) - w; *//*

            z = LOGPI - log(z) - w;
            return (z);
        }

        if (x < 13.0) {
            z = 1.0;
            p = 0.0;
            u = x;
            while (u >= 3.0) {
                p -= 1.0;
                u = x + p;
                z *= u;
            }
            while (u < 2.0) {
                if (u == 0.0)
		goto lgsing;
                z /= u;
                p += 1.0;
                u = x + p;
            }
            if (z < 0.0) {
	    *sign = -1;
                z = -z;
            } else
	    *sign = 1;
            if (u == 2.0)
                return (log(z));
            p -= 2.0;
            x = x + p;
            p = x * polevl(x, B, 5) / p1evl(x, C, 6);
            return (log(z) + p);
        }

        if (x > MAXLGM) {
            return (sign * Double.POSITIVE_INFINITY)
        }

        q = (x - 0.5) * log(x) - x + LS2PI;
        if (x > 1.0e8)
            return (q);

        p = 1.0 / (x * x);
        if (x >= 1000.0)
            q += ((7.9365079365079365079365e-4 * p
                    - 2.7777777777777777777778e-3) * p
                    + 0.0833333333333333333333) / x;
        else
            q += polevl(p, A, 4) / x;
        return (q);
    }

    static double polevl(double x, final double coef[], int N) {

*/
/*
        double ans;
        const double *p = coef;
        int i = N;

        ans = *p++;
        do
            ans = ans * x + *p++;
        while(--i);

        return ans ;
*//*


        double ans = coef[0];

        for (int i = N; i > 0; i--) {
            ans = ans * x + coef[i];
        }

        return ans;
    }

    static double p1evl(double x, final double coef[], int N) {

        */
/*
            double ans;
            const double *p = coef;
            int i = N;

            ans = *p++;
            do
                ans = ans * x + *p++;
            while(--i);

            return ans ;
        *//*


        double ans = x + coef[0];

        for (int i = N; i > 1; i--) {
            ans = ans * x + coef[i];
        }

        return ans;
    }

    static double incbcf(double a, double b, double x) {
        double xk, pk, pkm1, pkm2, qk, qkm1, qkm2;
        double k1, k2, k3, k4, k5, k6, k7, k8;
        double r, t, ans, thresh;
        int n;

        k1 = a;
        k2 = a + b;
        k3 = a;
        k4 = a + 1.0;
        k5 = 1.0;
        k6 = b - 1.0;
        k7 = k4;
        k8 = a + 2.0;

        pkm2 = 0.0;
        qkm2 = 1.0;
        pkm1 = 1.0;
        qkm1 = 1.0;
        ans = 1.0;
        r = 1.0;
        n = 0;
        thresh = 3.0 * MACHEP;
        cdone:
        {
            do {

                xk = -(x * k1 * k2) / (k3 * k4);
                pk = pkm1 + pkm2 * xk;
                qk = qkm1 + qkm2 * xk;
                pkm2 = pkm1;
                pkm1 = pk;
                qkm2 = qkm1;
                qkm1 = qk;

                xk = (x * k5 * k6) / (k7 * k8);
                pk = pkm1 + pkm2 * xk;
                qk = qkm1 + qkm2 * xk;
                pkm2 = pkm1;
                pkm1 = pk;
                qkm2 = qkm1;
                qkm1 = qk;

                if (qk != 0)
                    r = pk / qk;
                if (r != 0) {
                    t = abs((ans - r) / r);
                    ans = r;
                } else
                    t = 1.0;

                if (t < thresh)
                    break cdone;

                k1 += 1.0;
                k2 += 1.0;
                k3 += 2.0;
                k4 += 2.0;
                k5 += 1.0;
                k6 -= 1.0;
                k7 += 2.0;
                k8 += 2.0;

                if ((abs(qk) + abs(pk)) > big) {
                    pkm2 *= biginv;
                    pkm1 *= biginv;
                    qkm2 *= biginv;
                    qkm1 *= biginv;
                }
                if ((abs(qk) < biginv) || (abs(pk) < biginv)) {
                    pkm2 *= big;
                    pkm1 *= big;
                    qkm2 *= big;
                    qkm1 *= big;
                }
            }
            while (++n < 300);
        }

        return (ans);
    }

    */
/* Continued fraction expansion #2 for incomplete beta integral *//*


    static double incbd(double a, double b, double x) {
        double xk, pk, pkm1, pkm2, qk, qkm1, qkm2;
        double k1, k2, k3, k4, k5, k6, k7, k8;
        double r, t, ans, z, thresh;
        int n;

        k1 = a;
        k2 = b - 1.0;
        k3 = a;
        k4 = a + 1.0;
        k5 = 1.0;
        k6 = a + b;
        k7 = a + 1.0;
        ;
        k8 = a + 2.0;

        pkm2 = 0.0;
        qkm2 = 1.0;
        pkm1 = 1.0;
        qkm1 = 1.0;
        z = x / (1.0 - x);
        ans = 1.0;
        r = 1.0;
        n = 0;
        thresh = 3.0 * MACHEP;
        cdone:
        {
            do {

                xk = -(z * k1 * k2) / (k3 * k4);
                pk = pkm1 + pkm2 * xk;
                qk = qkm1 + qkm2 * xk;
                pkm2 = pkm1;
                pkm1 = pk;
                qkm2 = qkm1;
                qkm1 = qk;

                xk = (z * k5 * k6) / (k7 * k8);
                pk = pkm1 + pkm2 * xk;
                qk = qkm1 + qkm2 * xk;
                pkm2 = pkm1;
                pkm1 = pk;
                qkm2 = qkm1;
                qkm1 = qk;

                if (qk != 0)
                    r = pk / qk;
                if (r != 0) {
                    t = abs((ans - r) / r);
                    ans = r;
                } else
                    t = 1.0;

                if (t < thresh)
                    break cdone;

                k1 += 1.0;
                k2 -= 1.0;
                k3 += 2.0;
                k4 += 2.0;
                k5 += 1.0;
                k6 += 1.0;
                k7 += 2.0;
                k8 += 2.0;

                if ((abs(qk) + abs(pk)) > big) {
                    pkm2 *= biginv;
                    pkm1 *= biginv;
                    qkm2 *= biginv;
                    qkm1 *= biginv;
                }
                if ((abs(qk) < biginv) || (abs(pk) < biginv)) {
                    pkm2 *= big;
                    pkm1 *= big;
                    qkm2 *= big;
                    qkm1 *= big;
                }
            }
            while (++n < 300);

        }

        return (ans);
    }

*/
}
