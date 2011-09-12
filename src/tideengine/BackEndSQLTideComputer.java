package tideengine;


import coreutilities.sql.SQLUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import java.util.ArrayList;

public class BackEndSQLTideComputer
{
  private static boolean verbose = false;
    
  public static ArrayList<Coefficient> buildSiteConstSpeed(Connection conn) throws Exception
  {
    ArrayList<Coefficient> csal = new ArrayList<Coefficient>();

    String coeffQuery = "select coeffname, coeffvalue from speedconstituents sc, coeffdefs cd where sc.coeffname = cd.name order by cd.rank";
    Statement query = conn.createStatement();
    ResultSet rs = query.executeQuery(coeffQuery);
    while (rs.next())
    {
      String name = rs.getString(1);
      double value = rs.getDouble(2);
      Coefficient coef = new Coefficient(name, value * TideUtilities.COEFF_FOR_EPOCH);
      csal.add(coef);
    }
    rs.close();
    query.close();
    return csal;
  }
  
  public static double getAmplitudeFix(Connection conn, int year, String name) throws Exception
  {
    double d = 0;
    try
    {
      String queryStr = "select value from nodefactors where coeffname = ? and year = ?";
      PreparedStatement pstmt = conn.prepareStatement(queryStr);
      pstmt.setString(1, name);
      pstmt.setInt(2, year);
      ResultSet rs = pstmt.executeQuery();
      while (rs.next())
      {
        d = rs.getDouble(1);
      }
      rs.close();
      pstmt.close();
    }
    catch (Exception ex)
    {
      System.err.println("Error for [" + name + "] in [" + year + "]");
      throw ex;
    }
    return d;
  }
  
  public static double getEpochFix(Connection conn, int year, String name) throws Exception
  {
    double d = 0;
    try
    {
      String queryStr = "select value from equilibriums where coeffname = ? and year = ?";
      PreparedStatement pstmt = conn.prepareStatement(queryStr);
      pstmt.setString(1, name);
      pstmt.setInt(2, year);
      ResultSet rs = pstmt.executeQuery();
      while (rs.next())
      {
        d = rs.getDouble(1) * TideUtilities.COEFF_FOR_EPOCH;
      }
      rs.close();
      pstmt.close();
    }
    catch (Exception ex)
    {
      System.err.println("Error for [" + name + "] in [" + year + "]");
      throw ex;
    }
    return d;
  }
  
  public static TideStation findTideStation(String stationName, int year, Connection conn) throws Exception
  {
    String queryOne = "select name, latitude, longitude, tzoffset, tzname, baseheightvalue, baseheightunit from stations where name like ?";
    //                        1     2         3          4         5       6                7
    String queryTwo = "select coeffname, amplitude, epoch from stationdata sd, coeffdefs cd where sd.stationname = ? and sd.coeffname = cd.name order by cd.rank";
    //                        1          2          3
    PreparedStatement pstmt1 =  conn.prepareStatement(queryOne);
    PreparedStatement pstmt2 =  conn.prepareStatement(queryTwo);
    pstmt1.setString(1, "%" + stationName + "%");
    long before = System.currentTimeMillis();

    ResultSet rs1 = pstmt1.executeQuery();
    TideStation ts = null;
    while (rs1.next())
    {
      if (ts == null)
        ts = new TideStation();
      ts.setFullName(rs1.getString(1));
      ts.setLatitude(rs1.getDouble(2));
      ts.setLongitude(rs1.getDouble(3));
      ts.setTimeOffset(rs1.getString(4));
      ts.setTimeZone(rs1.getString(5));
      ts.setBaseHeight(rs1.getDouble(6));
      ts.setUnit(rs1.getString(7));
      String[] np = ts.getFullName().split(",");
      for (int i=0; i<np.length; i++)
        ts.getNameParts().add(np[(np.length - 1) - i]);
      pstmt2.setString(1, ts.getFullName());
      break; // Just the first one
    }
    rs1.close();
    pstmt1.close();
    
    ResultSet rs2 = pstmt2.executeQuery();
    while (rs2.next())
    {
      String name      = rs2.getString(1);
      double amplitude = rs2.getDouble(2);
      double epoch     = rs2.getDouble(3) * TideUtilities.COEFF_FOR_EPOCH;
      Harmonic h = new Harmonic(name, amplitude, epoch);
      ts.getHarmonics().add(h);
    }
    rs2.close();
    pstmt2.close();
    long after = System.currentTimeMillis();
    if (verbose) System.out.println("Finding the node took " + Long.toString(after - before) + " ms");
    
    // Fix for the given year
//  System.out.println("We are in " + year);
    // Correction to the Harmonics
    if (ts != null)
    {
      for (Harmonic harm : ts.getHarmonics())
      {
        String name = harm.getName();
        if (!"x".equals(name)) // Does no happen in the DB case
        {
          double amplitudeFix = getAmplitudeFix(conn, year, name);
          double epochFix     = getEpochFix(conn, year, name);
          
          harm.setAmplitude(harm.getAmplitude() * amplitudeFix);
          harm.setEpoch(harm.getEpoch() - epochFix);
        }
      }
    }
    if (verbose) System.out.println("Sites coefficients of [" + ts.getFullName() + "] fixed for " + year);
    
    return ts;
  }
  
  public static void setVerbose(boolean verbose)
  {
    BackEndSQLTideComputer.verbose = verbose;
  }
  
  public static void main(String[] args) throws Exception
  {
    long before = System.currentTimeMillis();
    Connection conn = SQLUtil.getConnection("C:\\_mywork\\dev-corner\\olivsoft\\all-db", "TIDES", "tides", "tides");
    long after = System.currentTimeMillis();
    System.out.println("Connected in " + Long.toString(after - before) + " ms");
    System.out.println("----------------------");
    before = System.currentTimeMillis();
    ArrayList<Coefficient> alc = buildSiteConstSpeed(conn);
    after = System.currentTimeMillis();
    System.out.println("ArrayList generated in " + Long.toString(after - before) + " ms");
    System.out.println("----------------------");
    before = System.currentTimeMillis();
    double d = getAmplitudeFix(conn, 2011, "J1");
    after = System.currentTimeMillis();
    System.out.println("ArrayList generated in " + Long.toString(after - before) + " ms");
    System.out.println("D:" + d + " in " + Long.toString(after - before) + " ms");
    System.out.println("----------------------");
    before = System.currentTimeMillis();
    d = getEpochFix(conn, 2011, "J1");
    after = System.currentTimeMillis();
    System.out.println("D:" + d + " in " + Long.toString(after - before) + " ms");
    System.out.println("----------------------");
    before = System.currentTimeMillis();
    TideStation ts = findTideStation("Saint-Malo, France", 2011, conn);
    after = System.currentTimeMillis();
    System.out.println("Station \"" + ts.getFullName() + "\" found in " + Long.toString(after - before) + " ms");
    System.out.println("Done.");
  }
}