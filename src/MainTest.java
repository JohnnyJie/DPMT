import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import dao.BaseDao;
import model.ConstraintStru;
import model.QueriesStru;
import model.RandomMarkov;
import model.TableStru;
import utils.ConstraintRewrite2;
import utils.JdbcUtils;

/**
 * 
 * @author Jie
 *
 */
public class MainTest {

	private static String constraints = "borrow(a,b,c),borrow(d,b,e) -:  a=d,c=e";
	private float epsilon = 0.1f;
	private float theta = 0.01f;
	private static Connection c;

	private static HashMap<String, ArrayList> tableMap;
	private ConstraintRewrite2 constraintRewrite;
	private static JdbcUtils jdbcUtils = new JdbcUtils();
	private static BaseDao baseDao = new BaseDao();

	public static void main(String[] args) throws SQLException {
		// TODO Auto-generated method stub

		MainTest test = new MainTest();
		c = baseDao.connectDB(); // init the connection of the database
		tableMap = baseDao.getTableSchema(c); // the schema of the database
		String sql = "SELECT  *\n" + "FROM  borrow\n" + "WHERE rid = '3'";
		test.sampleFramework(constraints.trim(), sql, 0);

		/*
		 * BaseDao basedao = new BaseDao(); ArrayList<String> tableNames =
		 * basedao.getTableNames(c); jdbcUtils.DropDView(c, tableNames);
		 * jdbcUtils.DropDTable(c, tableNames);
		 */

	}

	/********
	 *
	 * @param constraint
	 * @param
	 * @return
	 * @throws SQLException
	 *             find the violation tuples and regarding table, and save them in
	 *             the del_ table
	 */
	public ConstraintStru violationCheck(String constraint, int sequence) throws SQLException {

		constraintRewrite = new ConstraintRewrite2();

		/**********
		 * if has two "reader" need to write as "reader","reader'" ex.
		 * reader(firstname,lastname,rid,born,gender,phone),reader'(firstname,lastname,rid,born,gender,phone)
		 * -: [ false |reader.rid = reader'.rid ,reader.firstname = reader'.firtname]
		 * reader(a,b,c,d,e,f), reader'(g,h,c,i,j,k),.... -: [ false |a=g,...]
		 *********/
		// "reader(a,b,c,d,e,f),reader'(g,h,c,i,j,k) -: [ false |a=g]"
		constraintRewrite.parse(constraint);

		/*********
		 * check the constraint format
		 *********/
		if (!constraintRewrite.tbFormatCheck(tableMap)) {
			System.err.println("constraint table schema error");
			return null;
		}

		/*********
		 * constraint rewrite and get violation tuples
		 *********/

		// { ........ }
		// <"att1 att2 att3",ArrayList<tuples>>
		ArrayList<String> consTbLst = new ArrayList<>(); // all the regarding table in a constraint
		for (TableStru tbStru : constraintRewrite.getTableList()) {
			consTbLst.add(tbStru.getTableName());
		}
		// { ........ }
		String[] depSqlArray = constraintRewrite.rewrite(tableMap);
		// String[ sql , attName1,attName2...]
		ArrayList<HashMap> vioTupleMap = constraintRewrite.getVioTuples(depSqlArray, c, tableMap);

		// create deletion table with same structure and store them in the deletion
		// table

		/*
		 * String createDelTable = "CREATE TABLE del_" + depSqlArray[0] + sequence +
		 * " AS SELECT * FROM " + depSqlArray[0] + " WHERE 1=2;"; String createDelSql =
		 * constraintRewrite.createDeletionTableSql(depSqlArray[0],c,
		 * tableMap.get(depSqlArray[0]),vioTuples, sequence);
		 * postgreSQLJDBC.execute(c,createDelTable,false);
		 * postgreSQLJDBC.execute(c,createDelSql,false);
		 */

		ConstraintStru constraintStru = new ConstraintStru(vioTupleMap, depSqlArray);
		// System.out.println(sql);

		return constraintStru;
	}

	public void sampleFramework(String constraint, String sql, int sequence) throws SQLException {
		BaseDao basedao = new BaseDao();
		int count = 0;

		Random random = new Random(System.currentTimeMillis());
		int m = (int) ((1 / (2 * epsilon * epsilon)) * Math.log(2 / theta));
		ArrayList<String> tableNames = basedao.getTableNames(c);
		QueriesStru stru = jdbcUtils.splitQuery(sql, c);

		ConstraintStru constraintStru = violationCheck(constraint, sequence);
		HashMap<ArrayList<String>, Integer> tupleList = new HashMap<>();
		try {
			// Run Row(SQL(theta)) for each constraint

			for (int i = 0; i < m; i++) {
				System.out.println("the " + (i + 1) + " round!");

				ArrayList<TableStru> tableList = constraintRewrite.getTableList();

				RandomMarkov randomMarkov = new RandomMarkov(constraintStru, random, tableList, tableMap);

				// create delete table for each table
				jdbcUtils.createDeleteTbale(c, tableNames);

				// store tuples to delete
				ArrayList<HashMap> dList = new ArrayList<HashMap>();
				// markov chain provide the tuples which to delete next
				while (randomMarkov.hasNext()) {

					HashMap tuple = randomMarkov.next();
					String tableName = (String) tuple.get("tableName");
					tuple.put("tableName", "del_" + tableName);
					dList.add(tuple);

					// reader_rid,reader_firstname ...reader'_rid
					// System.out.println(tuple);
					// jdbcUtils.updateTable(tuple, "del_" + tableName, c);
					// System.out.println(tuple);
				}
				jdbcUtils.InsertData(dList, c);
				jdbcUtils.CreateDeleteView_NOTEXIST(c, tableNames);

				tupleList = jdbcUtils.queryRewrite(stru, c, tupleList);

				jdbcUtils.DropDView(c, tableNames);
				jdbcUtils.DropDTable(c, tableNames);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		for (String att : stru.getAtt()) {
			// System.out.println(att);
			System.out.print(att + "    ");
		}
		System.out.println();

		for (Map.Entry<ArrayList<String>, Integer> entry : tupleList.entrySet()) {
			ArrayList<String> tuple = entry.getKey();

			for (String att : tuple) {

				System.out.print(att + "\t");
			}
			float p = (float) entry.getValue() / (float) m;
			System.out.println("probablity: " + p);

		}

	}

}
