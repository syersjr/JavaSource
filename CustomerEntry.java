/* This program is for Customer Entry 
 *   written by John R. Syers and completed on 10/03/2013
 * 
 *   Features of this program:
 *   	- Written in Java so it is portable!
 *   	- Uses GUI interface to Select, Add, Change & Delete Customer records
 *   	- Is client server capable, using Apache Derby for the database
 *   	- Record locking is accomplished by an in-record field
 *   	- Basic print of Customer Listing - somewhat readable if printed in landscape mode
 *   
 *   If I had all of the time in the world (or was getting paid) I would like to enhance the following (not in any specific order)
 *   	1) Fix 'minor' problem when 2nd server program is launched, while another server is already running - ugly
 *   	2) Look at implementing Derby's 'in-house' record locking
 *   	3) Fix table to be sorted (add ORDER BY NAME to SQL) - would need to fix 'Selected Row' save logic to be based on Name
 *   	4) Fix the printing system to create a better report - loose the graphical table look
 *   	5) Find a better way than using timer (every 3 seconds) to update database in table
 *   	6) Use class better - class has a lot of really really cool features, but I didn't see them applying here
 *   	7) Fix the State popup to work by pressing the letter
 *   	8) The fonts in the JMenu & JMenuItem are too small
 *   	9) Implement a better error processor for the SQL errors & Derby exceptions
 *   	10) If used in production would put client IP address on command line (or in windows settings, etc.)
 *   	   
 *    This program needs the following Jar files
 *    		- derby 		(for server)
 *    		- derby.net		(for server)
 *    		- derby.tools	(for server)
 *    		- derby.client	(for client)
 *    			- http://db.apache.org/derby/releases/release-10.10.1.1.cgi
 *    		- rs2xml		(for server & client) - used to 'easily' populate the table from the database
 *    			- http://technojeeves.com/joomla/index.php/free/59-resultset-to-tablemodel
 *    
 *    10/04/13 (JRS): Comments below added
 *    	- How to run this program:
 *    		- Server
 *    			-this program uses the embeded Apache Derby Server - this must be installed first:
 *    				- http://db.apache.org/derby/papers/DerbyTut/install_software.html#derby_configure
 *          	- command line switches
 *          		- /server
 *          			- (Required) - indicates the program is running as the server
 *          		- /new
 *          			- (Required) - For 1st run (only) to create the database and CUSTOMERS table
 *          			- (Optional) - Only used to recreate database - DATA WILL BE DELETED!
 *          - Client
 *          	- this program requires the CLASSPATH to point to derby.client.jar & rs2xml.jar (above)
 *          		- See: http://db.apache.org/derby/papers/DerbyClientSpec.html
 *          	- command line switches: (none)
 *          	- you will need to know the IP address of the Server, you will be prompted for it.
 *   	
 */
import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.Dimension;
import java.awt.event.*;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.print.PrinterException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Properties;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpringLayout;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.MaskFormatter;
import javax.swing.Timer;

import net.proteanit.sql.DbUtils;

public class CustomerEntry extends JFrame implements ActionListener {
	  protected JButton btnAdd, btnChange, btnDelete, btnOk, btnCancel;
	private static final long serialVersionUID = 1L;
	private JTextField textName;
	private JTextField textAddress;
	private JTextField textCity;
	private JTextField textEmail;
	private JFormattedTextField textYTDSales;
	private JFormattedTextField textLastInv;
	private Choice choiceState;
	private JFormattedTextField textZip;
	private JFormattedTextField textPhone;
	private JFormattedTextField textLastContact;
	private JScrollPane scrollPane;
	private CustomerEntry frame;
	private CustomerRec dataRec;
	private DefaultTableModel model;
	static JTable table;
	static DataBaseAccess dba;
	public static Connection conn;
	public PreparedStatement prepStmt;
	
	/**
	 * Launch the application.
	 */
	public static void main(String args[]) throws UnknownHostException {
//			Process Command Line Arguments
			if (args.length < 1 || args.length > 2) {
				Global.isServer = false;
				Global.exitMsg  = "Are you sure?";
				Global.newDB = false;
			} else {
				Global.newDB = false;
				if (args[0].equals("/server") || args[0].equals("/SERVER")) {
					Global.isServer = true;
					Global.exitMsg  = "Network Connections will be lost - are you sure?";
				}
				if (args.length == 2 &&
						   (args[1].equals("/NEW") || args[1].equals("/new"))) {
					Global.newDB = true;
				} else {
					Global.newDB = false;
				}
			}
			
		// This is needed because we use Swing for the GUI
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					final CustomerEntry frame = new CustomerEntry();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				} 
			}
		});
	}

	/**
	 * Create the frame.
	 */
	public CustomerEntry() {
		dataRec = new CustomerRec();
		dba = new DataBaseAccess();
		// initialize - If not server prompts for IP address & validates; opens data base file 
		try {
			initialize();
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		getContentPane().setFont(new Font("Tahoma", Font.PLAIN, 16));
		setTitle("Customer Entry");
		//setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		setBounds(100, 100, 950, 600);
		addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
            	// stop refreshing table from database
            	timer.stop();
                if(JOptionPane.showConfirmDialog(frame,Global.exitMsg) == JOptionPane.OK_OPTION){
                    setVisible(false);
                    dispose();
                    // Clear in-use flag if needed
                    processCancel();
            		// Shutdown database here 
            		dba.shutDown();
                } else {
                	// continue refreshing table from database
                	timer.restart();
                }
            }
		});
		
		// Setup Pull down menu (used for File: Print & Exit)
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);
		JMenu mnNewMenu = new JMenu("File");
		mnNewMenu.setMnemonic(KeyEvent.VK_F);
		JMenuItem menuFilePrint = new JMenuItem("Print");
		menuFilePrint.addActionListener(new MenuActionListener());
		menuFilePrint.setMnemonic(KeyEvent.VK_P);
	    JMenuItem menuFileExit = new JMenuItem("Exit");
	    menuFileExit.addActionListener(new MenuActionListener());
	    menuFileExit.setMnemonic(KeyEvent.VK_X);
		menuBar.add(mnNewMenu);
		mnNewMenu.add(menuFilePrint);
		mnNewMenu.add(menuFileExit);
		SpringLayout springLayout = new SpringLayout();
		getContentPane().setLayout(springLayout);
		
		// Setup Name label
		JLabel lblName = new JLabel("Name");
		springLayout.putConstraint(SpringLayout.WEST, lblName, 26, SpringLayout.WEST, getContentPane());
		lblName.setFont(new Font("Tahoma", Font.BOLD, 16));
		lblName.setHorizontalAlignment(SwingConstants.RIGHT);
		getContentPane().add(lblName, BorderLayout.NORTH);
		
		// Setup Address label
		JLabel lblAddress = new JLabel("Address");
		springLayout.putConstraint(SpringLayout.WEST, lblAddress, 26, SpringLayout.WEST, getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, lblAddress, 0, SpringLayout.EAST, lblName);
		lblAddress.setFont(new Font("Tahoma", Font.BOLD, 16));
		lblAddress.setHorizontalAlignment(SwingConstants.RIGHT);
		getContentPane().add(lblAddress);
		
		// Setup City label
		JLabel lblCity = new JLabel("City");
		springLayout.putConstraint(SpringLayout.WEST, lblCity, 10, SpringLayout.WEST, getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, lblName, 0, SpringLayout.EAST, lblCity);
		springLayout.putConstraint(SpringLayout.NORTH, lblCity, 28, SpringLayout.SOUTH, lblAddress);
		lblCity.setFont(new Font("Tahoma", Font.BOLD, 16));
		lblCity.setHorizontalAlignment(SwingConstants.RIGHT);
		getContentPane().add(lblCity);
		
		// Setup State label
		JLabel lblState = new JLabel("State");
		springLayout.putConstraint(SpringLayout.NORTH, lblState, 0, SpringLayout.NORTH, lblCity);
		springLayout.putConstraint(SpringLayout.EAST, lblState, -469, SpringLayout.EAST, getContentPane());
		lblState.setHorizontalAlignment(SwingConstants.RIGHT);
		lblState.setFont(new Font("Tahoma", Font.BOLD, 16));
		getContentPane().add(lblState);
		
		// Setup ZIP Code label
		JLabel lblZip = new JLabel("ZIP");
		springLayout.putConstraint(SpringLayout.NORTH, lblZip, 126, SpringLayout.NORTH, getContentPane());
		springLayout.putConstraint(SpringLayout.SOUTH, lblZip, 0, SpringLayout.SOUTH, lblCity);
		lblZip.setHorizontalAlignment(SwingConstants.RIGHT);
		lblZip.setFont(new Font("Tahoma", Font.BOLD, 16));
		getContentPane().add(lblZip);
		
		// Setup Phone label
		JLabel lblPhone = new JLabel("Phone");
		springLayout.putConstraint(SpringLayout.WEST, lblPhone, 10, SpringLayout.WEST, getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, lblCity, 0, SpringLayout.EAST, lblPhone);
		springLayout.putConstraint(SpringLayout.NORTH, lblPhone, 28, SpringLayout.SOUTH, lblCity);
		lblPhone.setHorizontalAlignment(SwingConstants.RIGHT);
		lblPhone.setFont(new Font("Tahoma", Font.BOLD, 16));
		getContentPane().add(lblPhone);
		
		// Setup email label
		JLabel lblEmail = new JLabel("email");
		springLayout.putConstraint(SpringLayout.NORTH, lblEmail, 0, SpringLayout.NORTH, lblPhone);
		springLayout.putConstraint(SpringLayout.WEST, lblEmail, 0, SpringLayout.WEST, lblState);
		springLayout.putConstraint(SpringLayout.EAST, lblEmail, -469, SpringLayout.EAST, getContentPane());
		lblEmail.setHorizontalAlignment(SwingConstants.RIGHT);
		lblEmail.setFont(new Font("Tahoma", Font.BOLD, 16));
		getContentPane().add(lblEmail);
		
		// Setup Year to Date Sales label
		JLabel lblYtdSales = new JLabel("YTD Sales");
		springLayout.putConstraint(SpringLayout.WEST, lblYtdSales, 10, SpringLayout.WEST, getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, lblYtdSales, -832, SpringLayout.EAST, getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, lblPhone, 0, SpringLayout.EAST, lblYtdSales);
		springLayout.putConstraint(SpringLayout.NORTH, lblYtdSales, 28, SpringLayout.SOUTH, lblPhone);
		lblYtdSales.setFont(new Font("Tahoma", Font.BOLD, 16));
		lblYtdSales.setHorizontalAlignment(SwingConstants.RIGHT);
		getContentPane().add(lblYtdSales);
		
		// Setup Last Invoice # label
		JLabel lblLastInvoice = new JLabel("Last Invoice #");
		springLayout.putConstraint(SpringLayout.NORTH, lblLastInvoice, 0, SpringLayout.NORTH, lblYtdSales);
		lblLastInvoice.setHorizontalAlignment(SwingConstants.RIGHT);
		lblLastInvoice.setFont(new Font("Tahoma", Font.BOLD, 16));
		getContentPane().add(lblLastInvoice);
		
		// Setup Last Contact Date label
		JLabel lblLastContact = new JLabel("Last Contact");
		springLayout.putConstraint(SpringLayout.NORTH, lblLastContact, 0, SpringLayout.NORTH, lblYtdSales);
		springLayout.putConstraint(SpringLayout.WEST, lblLastContact, 589, SpringLayout.WEST, getContentPane());
		lblLastContact.setHorizontalAlignment(SwingConstants.RIGHT);
		lblLastContact.setFont(new Font("Tahoma", Font.BOLD, 16));
		getContentPane().add(lblLastContact);
		
		// Setup Name field
		textName = new JTextField();
		springLayout.putConstraint(SpringLayout.NORTH, textName, 26, SpringLayout.NORTH, getContentPane());
		springLayout.putConstraint(SpringLayout.NORTH, lblName, 1, SpringLayout.NORTH, textName);
		textName.setFont(new Font("Tahoma", Font.PLAIN, 16));
		getContentPane().add(textName);
		textName.setColumns(20);
		
		// Setup Address field
		textAddress = new JTextField();
		springLayout.putConstraint(SpringLayout.NORTH, textAddress, 25, SpringLayout.SOUTH, textName);
		springLayout.putConstraint(SpringLayout.NORTH, lblAddress, 1, SpringLayout.NORTH, textAddress);
		springLayout.putConstraint(SpringLayout.WEST, textName, 0, SpringLayout.WEST, textAddress);
		textAddress.setFont(new Font("Tahoma", Font.PLAIN, 16));
		textAddress.setColumns(20);
		getContentPane().add(textAddress);
		
		// Setup City field
		textCity = new JTextField();
		springLayout.putConstraint(SpringLayout.NORTH, textCity, 122, SpringLayout.NORTH, getContentPane());
		springLayout.putConstraint(SpringLayout.WEST, lblState, 6, SpringLayout.EAST, textCity);
		springLayout.putConstraint(SpringLayout.WEST, textAddress, 0, SpringLayout.WEST, textCity);
		textCity.setFont(new Font("Tahoma", Font.PLAIN, 16));
		textCity.setColumns(20);
		getContentPane().add(textCity);
		
		// Setup State pull down field
		choiceState = new Choice();
		springLayout.putConstraint(SpringLayout.NORTH, choiceState, 122, SpringLayout.NORTH, getContentPane());
		springLayout.putConstraint(SpringLayout.WEST, lblZip, 30, SpringLayout.EAST, choiceState);
		springLayout.putConstraint(SpringLayout.WEST, choiceState, 12, SpringLayout.EAST, lblState);
		springLayout.putConstraint(SpringLayout.SOUTH, choiceState, 0, SpringLayout.SOUTH, lblCity);
		choiceState.setFont(new Font("Dialog", Font.PLAIN, 13));
		String [] states = {"  ", "AK", "AL", "AR", "AZ", "CA", "CO", "CT", "DC", "DE", "FL", "GA",
				"HI", "IA", "ID", "IL", "IN", "KS", "KY", "LA", "MA", "MD", "ME", "MI",
			    "MN", "MO", "MS", "MT", "NC", "ND", "NE", "NH", "NJ", "NM", "NV", "NY",
			    "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VA", "VT",
			    "WA", "WI", "WV", "WY"};
		for (int i = 0; i < states.length; i++)
			choiceState.add(states[i]);
		getContentPane().add(choiceState);
		// Listener logic for pull down State field
		choiceState.addItemListener(new ItemListener(){
	        @Override
			public void itemStateChanged(ItemEvent ie)
	        {
	        dataRec.state = choiceState.getSelectedItem();
	        }
	    });	
		
		// Setup ZIP Code field (edited for '#####-****')
		MaskFormatter zipMask = null;
		try {
			zipMask = new MaskFormatter("#####-****");
			zipMask.setValidCharacters("0123456789 ");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		textZip = new JFormattedTextField(zipMask);
		springLayout.putConstraint(SpringLayout.NORTH, textZip, 122, SpringLayout.NORTH, getContentPane());
		springLayout.putConstraint(SpringLayout.WEST, textZip, 10, SpringLayout.EAST, lblZip);
		springLayout.putConstraint(SpringLayout.SOUTH, textZip, 2, SpringLayout.SOUTH, lblCity);
		springLayout.putConstraint(SpringLayout.EAST, textZip, 157, SpringLayout.EAST, lblZip);
		textZip.setFont(new Font("Tahoma", Font.PLAIN, 16));
		getContentPane().add(textZip);
		
		// Setup Phone Number field (edited for '(###) ###-####')
		MaskFormatter phoneMask = null;
		try {
			phoneMask = new MaskFormatter("(###) ###-####");
			phoneMask.setValidCharacters("0123456789");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		textPhone = new JFormattedTextField(phoneMask);
		springLayout.putConstraint(SpringLayout.EAST, textPhone, -676, SpringLayout.EAST, getContentPane());
		springLayout.putConstraint(SpringLayout.WEST, textCity, 0, SpringLayout.WEST, textPhone);
		springLayout.putConstraint(SpringLayout.SOUTH, textCity, -23, SpringLayout.NORTH, textPhone);
		springLayout.putConstraint(SpringLayout.NORTH, textPhone, -3, SpringLayout.NORTH, lblPhone);
		springLayout.putConstraint(SpringLayout.WEST, textPhone, 14, SpringLayout.EAST, lblPhone);
		textPhone.setFont(new Font("Tahoma", Font.PLAIN, 16));
		getContentPane().add(textPhone);
		
		// Setup email field
		textEmail = new JTextField();
		springLayout.putConstraint(SpringLayout.NORTH, textEmail, -3, SpringLayout.NORTH, lblPhone);
		springLayout.putConstraint(SpringLayout.WEST, textEmail, 10, SpringLayout.EAST, lblEmail);
		springLayout.putConstraint(SpringLayout.EAST, textEmail, -105, SpringLayout.EAST, getContentPane());
		textEmail.setFont(new Font("Tahoma", Font.PLAIN, 16));
		textEmail.setColumns(40);
		getContentPane().add(textEmail);
		
		// Setup Year to Date Sales field (edited for 15 digit number allowing ' ' or ' .' or ',')
		MaskFormatter ytdMask = null;
		try {
			ytdMask = new MaskFormatter("***************");
			ytdMask.setValidCharacters("0123456789., ");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		textYTDSales = new JFormattedTextField(ytdMask);
		springLayout.putConstraint(SpringLayout.NORTH, textYTDSales, 216, SpringLayout.NORTH, getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, textYTDSales, 158, SpringLayout.WEST, textName);
		springLayout.putConstraint(SpringLayout.WEST, lblLastInvoice, 24, SpringLayout.EAST, textYTDSales);
		springLayout.putConstraint(SpringLayout.WEST, textYTDSales, 0, SpringLayout.WEST, textName);
		springLayout.putConstraint(SpringLayout.SOUTH, textYTDSales, 0, SpringLayout.SOUTH, lblYtdSales);
		textYTDSales.setFont(new Font("Tahoma", Font.PLAIN, 16));
		textYTDSales.setColumns(20);
		getContentPane().add(textYTDSales);
		
		// Setup Last Invoice # field (edited for 10 digit number allowing ' ')
		MaskFormatter invMask = null;
		try {
			invMask = new MaskFormatter("**********");
			invMask.setValidCharacters("0123456789 ");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		textLastInv = new JFormattedTextField(invMask);
		springLayout.putConstraint(SpringLayout.NORTH, textLastInv, 216, SpringLayout.NORTH, getContentPane());
		springLayout.putConstraint(SpringLayout.WEST, textLastInv, 15, SpringLayout.EAST, lblLastInvoice);
		springLayout.putConstraint(SpringLayout.SOUTH, textLastInv, 0, SpringLayout.SOUTH, lblYtdSales);
		springLayout.putConstraint(SpringLayout.EAST, textLastInv, 136, SpringLayout.EAST, lblLastInvoice);
		textLastInv.setFont(new Font("Tahoma", Font.PLAIN, 16));
		textLastInv.setColumns(20);
		getContentPane().add(textLastInv);
		
		// Setup Last Contact Date field (Edited as '##/##/####')
		MaskFormatter dateMask = null;
		try {
			dateMask = new MaskFormatter("##/##/####");
			dateMask.setValidCharacters("0123456789");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		textLastContact = new JFormattedTextField(dateMask);
		springLayout.putConstraint(SpringLayout.NORTH, textLastContact, 216, SpringLayout.NORTH, getContentPane());
		springLayout.putConstraint(SpringLayout.WEST, textLastContact, 702, SpringLayout.WEST, getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, textLastContact, -130, SpringLayout.EAST, getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, lblLastContact, -11, SpringLayout.WEST, textLastContact);
		springLayout.putConstraint(SpringLayout.SOUTH, textLastContact, 0, SpringLayout.SOUTH, lblYtdSales);
		textLastContact.setFont(new Font("Tahoma", Font.PLAIN, 16));
		getContentPane().add(textLastContact);
		
		// Setup JTable - where all the 'magic' occurs
        // TableModel definition
        model = new DefaultTableModel();
        model.addColumn("Name");
        model.addColumn("Phone");
        model.addColumn("Address");
        model.addColumn("City");
        model.addColumn("State");
        model.addColumn("ZIP");
        model.addColumn("email");
        model.addColumn("YTD Sales");
        model.addColumn("Last Invoice #");
        model.addColumn("Last Contact");
        // Table definition
        table = new JTable(model);
        table.setPreferredScrollableViewportSize(new Dimension(500, 70));
        table.setFillsViewportHeight(true);
        table.setAutoCreateColumnsFromModel(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);
        table.getColumnModel().getColumn(4).setPreferredWidth(40);
        table.getColumnModel().getColumn(5).setPreferredWidth(90);
        table.getColumnModel().getColumn(6).setPreferredWidth(300);
        table.getColumnModel().getColumn(7).setPreferredWidth(100);
        table.getColumnModel().getColumn(8).setPreferredWidth(100);
        table.getColumnModel().getColumn(9).setPreferredWidth(100);
        // Initialize 'Selected' fields for 1st time
        Global.selectedKey = "";
        Global.selectedRow = 0;
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        	@Override
			public void valueChanged(ListSelectionEvent e) {
        		ListSelectionModel lsm = (ListSelectionModel) e.getSource();
        		if (e.getValueIsAdjusting())
        			// Value is being adjusted - skip out
        			return;
        		if (lsm.isSelectionEmpty())
        			// Selection is empty - skip out
        			return;
        		Global.selectedRow = table.getSelectedRow();
        		Global.selectedKey = table.getValueAt(table.getSelectedRow(), 0).toString();
        	}
        });
		// Setup Scroll Pane to display the table
		scrollPane = new JScrollPane(table);
		springLayout.putConstraint(SpringLayout.NORTH, scrollPane, 25, SpringLayout.SOUTH, lblYtdSales);
		springLayout.putConstraint(SpringLayout.WEST, scrollPane, 21, SpringLayout.WEST, getContentPane());
		springLayout.putConstraint(SpringLayout.SOUTH, scrollPane, -21, SpringLayout.SOUTH, getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, scrollPane, -229, SpringLayout.EAST, getContentPane());
		getContentPane().add(scrollPane);
        // Initialize ReFreshInProgress flag to false - used by updateTable & again via timer.start
        Global.dbRefreshInProgress = false;
        // Populate table from database
        updateTable();
        // Add timer loop to update table from database every 3 seconds
        timer.setDelay(3000);
        timer.start();
		
		// Setup Add button
		btnAdd = new JButton("Add");
		springLayout.putConstraint(SpringLayout.NORTH, btnAdd, 0, SpringLayout.NORTH, scrollPane);
		springLayout.putConstraint(SpringLayout.WEST, btnAdd, 22, SpringLayout.EAST, scrollPane);
		springLayout.putConstraint(SpringLayout.SOUTH, btnAdd, -245, SpringLayout.SOUTH, getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, btnAdd, -105, SpringLayout.EAST, getContentPane());
		btnAdd.setFont(new Font("Tahoma", Font.PLAIN, 16));
		getContentPane().add(btnAdd);
		btnAdd.setActionCommand("Add");
		
		// Setup Change button
		btnChange = new JButton("Change");
		springLayout.putConstraint(SpringLayout.NORTH, btnChange, 21, SpringLayout.SOUTH, btnAdd);
		springLayout.putConstraint(SpringLayout.WEST, btnChange, 0, SpringLayout.WEST, btnAdd);
		springLayout.putConstraint(SpringLayout.SOUTH, btnChange, -195, SpringLayout.SOUTH, getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, btnChange, 0, SpringLayout.EAST, textEmail);
		btnChange.setFont(new Font("Tahoma", Font.PLAIN, 16));
		getContentPane().add(btnChange);
		btnChange.setActionCommand("Change");
		
		// Setup Delete button
		btnDelete = new JButton("Delete");
		springLayout.putConstraint(SpringLayout.NORTH, btnDelete, 21, SpringLayout.SOUTH, btnChange);
		springLayout.putConstraint(SpringLayout.WEST, btnDelete, 0, SpringLayout.WEST, btnAdd);
		springLayout.putConstraint(SpringLayout.SOUTH, btnDelete, -145, SpringLayout.SOUTH, getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, btnDelete, 0, SpringLayout.EAST, textEmail);
		btnDelete.setFont(new Font("Tahoma", Font.PLAIN, 16));
		getContentPane().add(btnDelete);
		btnDelete.setActionCommand("Delete");
		
		// Setup OK button
		btnOk = new JButton("OK");
		this.getRootPane().setDefaultButton(btnOk);
		springLayout.putConstraint(SpringLayout.WEST, btnOk, 22, SpringLayout.EAST, scrollPane);
		springLayout.putConstraint(SpringLayout.SOUTH, btnOk, 0, SpringLayout.SOUTH, scrollPane);
		springLayout.putConstraint(SpringLayout.EAST, btnOk, -122, SpringLayout.EAST, getContentPane());
		btnOk.setFont(new Font("Tahoma", Font.PLAIN, 16));
		getContentPane().add(btnOk);
		btnOk.setActionCommand("Ok");
		
		// Setup Cancel button
		btnCancel = new JButton("Cancel");
		springLayout.putConstraint(SpringLayout.NORTH, btnCancel, 0, SpringLayout.NORTH, btnOk);
		springLayout.putConstraint(SpringLayout.WEST, btnCancel, 16, SpringLayout.EAST, btnOk);
		springLayout.putConstraint(SpringLayout.SOUTH, btnCancel, 0, SpringLayout.SOUTH, scrollPane);
		springLayout.putConstraint(SpringLayout.EAST, btnCancel, -21, SpringLayout.EAST, getContentPane());
		btnCancel.setFont(new Font("Tahoma", Font.PLAIN, 16));
		getContentPane().add(btnCancel);
		btnCancel.setActionCommand("Cancel");
		
		// Add Action Listeners for all buttons
		btnAdd.addActionListener(this);
		btnChange.addActionListener(this);
		btnDelete.addActionListener(this);
		btnOk.addActionListener(this);
		btnCancel.addActionListener(this);
		
		// Default Screen & Function to show the select screen
		Global.scrnMode = "Select";
		Global.scrnFunction = "";
		Global.saveKey = "";
		setupScreen();
				
	}
	
	// Used by the pull down menu command
	class MenuActionListener implements ActionListener {
		  @Override
		public void actionPerformed(ActionEvent e) {
			  switch (e.getActionCommand()) {
			  	case "Print":
			  		printTable();
			  		break;
			  	case "Exit":
			  		// stop refreshing table from database
			  		timer.stop();
			  		if(JOptionPane.showConfirmDialog(frame,Global.exitMsg) == JOptionPane.OK_OPTION){
			  			setVisible(false);
			  			dispose();
	                    // Clear in-use flag if needed
	                    processCancel();
			  			// Shutdown database here 
			  			dba.shutDown();
			  		} else {
			  			// continue refreshing table from database
			  			timer.start();
			  		}
			  		break;
			  	default: break;
			  }
		  }
		}

	// Action Listener logic for all buttons
	@Override
	public void actionPerformed(ActionEvent e) {
	    switch (e.getActionCommand()) {
	    	case "Add":
				Global.scrnFunction = "Add";
		    	Global.scrnMode = "Entry";
		    	setupScreen();
		    	clearScreenFields();
		    	textName.grabFocus();
		    	break;
	    	case "Change":
	    		changePressed();
		    	break;
	    	case "Delete":
	    		deletePressed();
		    	break;
	    	case "Ok":
	    		processOK();
	    		updateTable();
		    	break;
	    	case "Cancel":
	    		processCancel();
		    	Global.scrnFunction = "";
		    	Global.scrnMode = "Select";
		    	setupScreen();
		    	break;
		    default: break;
	    }
	}
	
	// This logic populates the JTable table from the derby database
	public static void updateTable() {
		// DBRefreshInProgress logic is designed to keep this routine from running via timer
		// and via stand alone call at the same time
		if (Global.dbRefreshInProgress == false) {
			Global.dbRefreshInProgress = true;
			try {
				Statement stmt = Global.conn.createStatement();
				ResultSet rs = stmt.executeQuery("Select NAME, PHONE, ADDRESS, CITY, STATE, ZIP, EMAIL, YTDSALES, LASTINVOICE, LASTCONTACT from CUSTOMERS");
				table.setModel(DbUtils.resultSetToTableModel(rs));
				stmt.close();
			} catch (SQLException sqle) {
				dba.printSQLException(sqle);
			} finally {
				//System.out.println("Dtatabase updated in table");
				Global.dbRefreshInProgress = false;
				if (Global.selectedRow != 0) {
					table.addRowSelectionInterval(Global.selectedRow, Global.selectedRow);
				}
			}
		}
	}
	
	// Prints the table using built-in print logic (looks pretty ugly) 
	public void printTable() {
		// Stop updating table from database - unexpected results can occur
		timer.stop();
		try {
			MessageFormat headerFormat = new MessageFormat("Customer Listing");
			MessageFormat footerFormat = new MessageFormat("Page {0}");
			table.print(JTable.PrintMode.FIT_WIDTH, headerFormat, footerFormat, true, null, true, null);
		} catch (PrinterException pe) {
			System.out.println("Error printing: " + pe.getMessage());
		}
		// Restart updating table from database
		timer.restart();
	}
	
	// This is used to call the (above) routine with a delay between execution
	Timer timer = new Timer(0, new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			updateTable();
		}
	});
	
	// Logic for when the Change button was pressed
	public void changePressed() {
		Global.messageOccurred = false;
		try {
			dba.readRecordForUpdate();
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		if (Global.messageOccurred == true)
			// an message was displayed in readRecordForUodate - we don't want to proceed
			return;
		
		Global.scrnFunction = "Change";
    	Global.scrnMode = "Entry";
    	setupScreen();
    	clearScreenFields();
    	moveDataRectoScreenFields();
    	textName.grabFocus();
	}
	
	// This is called by the pressing of the Delete button
	// It reads the selected record, makes sure it is not in use and marks it as in use
	public void deletePressed() {
		Global.messageOccurred = false;
		try {
			dba.readRecordForUpdate();
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		if (Global.messageOccurred == true)
			// an message was displayed in readRecordForUodate - we don't want to proceed
			return;
		
		// Set to show the 'Delete' select mode
    	Global.scrnFunction = "Delete";
    	Global.scrnMode = "Select";
    	setupScreen();
	}

	// This initializes the text entry fields - used by Add mode
	public void clearScreenFields() {
		textName.setText("");
		textAddress.setText("");
		textCity.setText("");
		choiceState.select("  ");
		textZip.setValue(null);
		textPhone.setValue(null);
		textEmail.setText("");
		textYTDSales.setValue(null);
		textLastInv.setValue(null);
		textLastContact.setValue(null);
	}

	// Setup screen fields - used by all modes
	public void setupScreen () {
		switch (Global.scrnMode) {
			case "Select":
				textName.setEnabled(false);
				textName.setVisible(false);
				textAddress.setEnabled(false);
				textAddress.setVisible(false);
				textCity.setEnabled(false);
				textCity.setVisible(false);
				choiceState.setEnabled(false);
				choiceState.setVisible(false);
				textZip.setEnabled(false);
				textZip.setVisible(false);
				textPhone.setEnabled(false);
				textPhone.setVisible(false);
				textEmail.setEnabled(false);
				textEmail.setVisible(false);
				textYTDSales.setEnabled(false);
				textYTDSales.setVisible(false);
				textLastInv.setEnabled(false);
				textLastInv.setVisible(false);
				textLastContact.setEnabled(false);
				textLastContact.setVisible(false);
				btnOk.setEnabled(false);
				btnOk.setVisible(false);
				btnCancel.setEnabled(false);
				btnCancel.setVisible(false);
				scrollPane.setEnabled(true);
				scrollPane.setVisible(true);
				table.setEnabled(true);
				if (Global.scrnFunction.equals("Delete")) {
					btnChange.setEnabled(false);
					btnChange.setVisible(false);
					btnAdd.setEnabled(false);
					btnAdd.setVisible(false);
					btnOk.setEnabled(true);
					btnOk.setVisible(true);
					btnCancel.setEnabled(true);
					btnCancel.setVisible(true);
					btnDelete.setEnabled(false);
				}
				else {
					btnDelete.setEnabled(true);
					btnDelete.setVisible(true);
					btnAdd.setEnabled(true);
					btnAdd.setVisible(true);
					btnChange.setEnabled(true);
					btnChange.setVisible(true);
					btnOk.setEnabled(false);
					btnOk.setVisible(false);
					btnCancel.setEnabled(false);
					btnCancel.setVisible(false);
				}
				break;
			case "Entry":
				textName.setEnabled(true);
				textName.setVisible(true);
				textAddress.setEnabled(true);
				textAddress.setVisible(true);
				textCity.setEnabled(true);
				textCity.setVisible(true);
				choiceState.setEnabled(true);
				choiceState.setVisible(true);
				textZip.setEnabled(true);
				textZip.setVisible(true);
				textPhone.setEnabled(true);
				textPhone.setVisible(true);
				textEmail.setEnabled(true);
				textEmail.setVisible(true);
				textYTDSales.setEnabled(true);
				textYTDSales.setVisible(true);
				textLastInv.setEnabled(true);
				textLastInv.setVisible(true);
				textLastContact.setEnabled(true);
				textLastContact.setVisible(true);
				scrollPane.setEnabled(false);
				scrollPane.setVisible(true);
				table.setEnabled(false);
				btnOk.setEnabled(true);
				btnOk.setVisible(true);
				btnCancel.setEnabled(true);
				btnCancel.setVisible(true);
				btnDelete.setEnabled(false);
				btnDelete.setVisible(false);
				switch (Global.scrnFunction){
					case "Add":
						btnAdd.setEnabled(false);
						btnChange.setEnabled(false);
						btnChange.setVisible(false);
						break;
					case "Change":
						btnChange.setEnabled(false);
						btnAdd.setEnabled(false);
						btnAdd.setVisible(false);
						break;
					default: break;
				}				
				break;
			default: break;
		} 
	}
	
	// This method is for when OK is pressed
	public void processOK() {
		switch (Global.scrnFunction) {
    		case "Add":
    			addAndChange();
    			break;
    		case "Change":
    			addAndChange();
    			break;
    		case "Delete":
    			deleteSelectedRecord();
    			break;
    		default: break;
		}
	}
	
	// This is logic shared by Add and change to do some basic editing,
	// write/update the data to disk and for change clear in use
	public void addAndChange() {
		if (textName.getText().equals("")) {
			JOptionPane.showMessageDialog(null, "Customer Name must be entered.");
			return;
		}
		moveScreenFieldstoDataRec();
		Global.lastSQLState = "none";
		switch (Global.scrnFunction) {
			case "Add":
				try {
					dba.addRecord();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
			case "Change":
				try {
					dba.changeRecord();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
		}
		if (Global.lastSQLState.equals("23505")) {
			JOptionPane.showMessageDialog(null, "A record already exists with this Customer Name.");
			return;
		}
		// Sets the next screen to display to Select
    	Global.scrnFunction = "";
    	Global.scrnMode = "Select";
    	setupScreen();
	}
	
	// Used by the delete logic - prompt the user, if OK delete record,
	// if Cancel or No - clear in use flag
	public void deleteSelectedRecord() {
		String DelConfirm = "Are you sure you want to delete: " + Global.saveKey + "?";
        if (JOptionPane.showConfirmDialog(frame,DelConfirm) == JOptionPane.OK_OPTION){
        	Global.lastSQLState = "none";
        	try {
        		dba.deleteRecord();
        	} catch (SQLException e) {
        		// TODO Auto-generated catch block
        		e.printStackTrace();
        	};
        } else {
        	// Clears 'records' in-use flag
        	processCancel();
        }
		// Sets the next screen to display to Select
    	Global.scrnFunction = "";
    	Global.scrnMode = "Select";
    	setupScreen();
	}
	
	// This is called by the Cancel button - it basically just clears in use if needed
	public void processCancel() {
		// This logic clears the InUse flag if it needs to 
		try {
			dba.clearInUse();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	// This maps the screen fields to the corresponding dataRec fields
	public void moveScreenFieldstoDataRec() {
			dataRec.name          = textName.getText();
			dataRec.address       = textAddress.getText();
			dataRec.city          = textCity.getText();
			dataRec.zip           = textZip.getText();
			//dataRec.State is already set in the jForm
			dataRec.phone         = textPhone.getText();
			dataRec.email         = textEmail.getText();
			dataRec.ytdSales      = textYTDSales.getText();
			dataRec.lastInvNo     = textLastInv.getText();
			dataRec.lastContact   = textLastContact.getText();
			dataRec.beingModified = false;
	}
	
	// This maps the dataRec fields to the corresponding screen fields
	public void moveDataRectoScreenFields() {
		textName.setText(dataRec.name);
		textAddress.setText(dataRec.address);
		textCity.setText(dataRec.city);
		textZip.setText(dataRec.zip);
		choiceState.select(dataRec.state);
		//dataRec.State is already set in the jForm
		textPhone.setText(dataRec.phone);
		textEmail.setText(dataRec.email);
		textYTDSales.setText(dataRec.ytdSales);
		textLastInv.setText(dataRec.lastInvNo);
		textLastContact.setText(dataRec.lastContact);
		// BeingModified is under program control
	}
		
	// Initialize logic - prompts for IP if not server & validates, opens SQL DB file & TCP/IP Socket
	public static void initialize() throws UnknownHostException {
		// Get the Server IP address
		if (Global.isServer == true) {
			// This is the server get 'this PCs' IP address
			Global.serverIP =  InetAddress.getLocalHost().getHostAddress();
		} else {
			// Not Server prompt user to enter Server IP
			String IP = JOptionPane.showInputDialog(null,"Please enter the Server IP Address",
						"Customer Entry", JOptionPane.INFORMATION_MESSAGE);
			// Validate IP address entered by user
			if (IP == null) {
			// User Pressed Cancel - exit program with return code = 0
				System.exit(0);
			}
			if (checkIPv4(IP) == true) {
				// IP address appears valid - save off
				Global.serverIP = IP;
			} else {
				// IP address is invalid display error and exit program with return code = 16
				JOptionPane.showMessageDialog(null,"Program Terminating - Invalid Server IP Address",
								"Customer Entry",JOptionPane.ERROR_MESSAGE);
				System.exit(16);
			}
		}
		
		// At this point we have either a server or a somewhat valid Server IP address - continue initialization
		
		// Open the database
		try {
			Global.conn = dba.getConnection();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Create Table if 'Create New Data Base' selected - Server Only
		if (Global.newDB == true)
			try {
				dba.createTable();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	
	// Validates the IP address entered is valid somewhat valid
	public static final boolean checkIPv4(String IP) {
	    boolean isIPv4;
	    try {
	    	final InetAddress inet = InetAddress.getByName(IP);
	    	isIPv4 = inet.getHostAddress().equals(IP)
	            && inet instanceof Inet4Address;
	    } catch (final UnknownHostException e) {
	    	isIPv4 = false;
	    }
	    return isIPv4;
	}

	// Defines Global variables
	public static class Global {
		public static boolean isServer;
		public static boolean newDB;
		public static boolean dbRefreshInProgress;
		public static boolean messageOccurred;
		public static int    selectedRow;
		public static String lastSQLState;
		public static String serverIP;
		public static String scrnMode;
		public static String scrnFunction;
		public static String exitMsg;
		public static String selectedKey;
		public static String saveKey;
		public static Connection conn;
	}
	
	// Defines the Customer Record class
	public class CustomerRec {
		String  name;
		String  address;
		String  city;
		String  state;
		String  zip;
		String  phone;
		String  email;
		String  ytdSales;
		String  lastInvNo;
		String  lastContact;
		boolean beingModified;
	}
	
	// Class for Derby data base access - handles all Database I/O 
	public class DataBaseAccess {
		public String dbName;
		public String driver;
		public String protocol;
		public String framework;
		public Connection conn;
		public ResultSet rs;
		public PreparedStatement prepStmt;
		
		// Opens Derby data base - will create new DB if '/new' command line option used on server
		public Connection getConnection() throws SQLException {
			dbName = "derbyDB";
			if (Global.isServer == true) {
			    framework = "embedded";
			    driver    = "org.apache.derby.jdbc.EmbeddedDriver";
			    protocol  = "jdbc:derby:";
			}
			else {
	            framework = "derbyclient";
	            driver    = "org.apache.derby.jdbc.ClientDriver";
	            //protocol  = "jdbc:derby://localhost:1527/";
	            protocol  = "jdbc:derby://" + Global.serverIP + ":1527/";
			}

	        Connection conn = null;
	        Properties props = new Properties(); // connection properties
	        props.put("user", "user1");
	        props.put("password", "user1");
	        if (Global.newDB == true) {
		        conn = DriverManager.getConnection(protocol + dbName
			             + ";create=true", props);
			        System.out.println("Connected to and created database " + dbName);
	        }
	        else {
		        conn = DriverManager.getConnection(protocol + dbName
			             + ";create=false", props);
			        System.out.println("Connected to database " + dbName);
	        }
	        return conn;
		}

		// This shuts down the database
		public void shutDown() {
			try {
            /*
             * In embedded mode, an application should shut down the database.
             * If the application fails to shut down the database,
             * Derby will not perform a checkpoint when the JVM shuts down.
             * This means that it will take longer to boot (connect to) the
             * database the next time, because Derby needs to perform a recovery
             * operation.
             *
             * It is also possible to shut down the Derby system/engine, which
             * automatically shuts down all booted databases.
             *
             * Explicitly shutting down the database or the Derby engine with
             * the connection URL is preferred. This style of shutdown will
             * always throw an SQLException.
             *
             * Not shutting down when in a client environment, see method
             * Javadoc.
             */
				if (framework.equals("embedded"))
				{
					try
					{
                    // the shutdown=true attribute shuts down Derby
						DriverManager.getConnection("jdbc:derby:;shutdown=true");
					}
					catch (SQLException se)
					{
						if (( (se.getErrorCode() == 50000)
                            && ("XJ015".equals(se.getSQLState()) ))) {
							// we got the expected exception
							System.out.println(dbName + " shut down normally");
							// Note that for single database shutdown, the expected
							// SQL state is "08006", and the error code is 45000.
						} else {
                        	// if the error code or SQLState is different, we have
                        	// an unexpected exception (shutdown failed)
                        	System.err.println(dbName + " did not shut down normally");
                        	printSQLException(se);
                    }
                }
            }
			} finally {
            // release all open resources to avoid unnecessary memory usage
            //Connection
				try {
					if (conn != null) {
						conn.close();
						conn = null;
                }
				} catch (SQLException sqle) {
					printSQLException(sqle);
				}
			}
		}
	
		// This is only called by the Server with '/new' command line option
		// It first deletes the CUSTOMERS table & then re-creates it
		public void createTable () throws SQLException {
			// Delete the CUSTOMERS table - if it exists
		    String createString =
			        "drop table CUSTOMERS"; 
			Statement stmt = null;
			try {
				stmt = Global.conn.createStatement();
			    stmt.executeUpdate(createString);
			} catch (SQLException sqle) {
				System.out.println("CUSTOMERS table did not exist in " + dbName);
			} finally {
			    System.out.println("CUSTOMERS table removed from " + dbName);
			    if (stmt != null) { stmt.close(); }
			}

			// Add the CUSTOMERS table into the database
		    createString =
			        "create table CUSTOMERS          " 
			        +  "  (NAME          varchar(30) NOT NULL PRIMARY KEY, "
			        +  "   ADDRESS       varchar(30),                      " 
			        +  "   CITY          varchar(30),                      "
			        +  "   STATE         varchar(02),                      "
			        +  "   ZIP           varchar(10),                      "
			        +  "   PHONE         varchar(15),                      "
			        +  "   EMAIL         varchar(60),                      "
			        +  "   YTDSALES      varchar(15),                      "
			        + "    LASTINVOICE   varchar(10),                      "
			        +  "   LASTCONTACT   varchar(10),                      "
			        +  "   BEINGMODIFIED boolean)                          ";
			try {
				stmt = Global.conn.createStatement();
				stmt.executeUpdate(createString);
			} catch (SQLException sqle) {
				printSQLException(sqle);
			} finally {
				System.out.println("CUSTOMERS table added to " + dbName);
				if (stmt != null) { stmt.close(); }
			}    
		}
		
		// This is used by 'Add' mode (on OK) to add the record from the screen into the database
		public void addRecord () throws SQLException {
			String insertTableSQL = "INSERT INTO CUSTOMERS"
					+ "(NAME, ADDRESS, CITY, STATE, ZIP, PHONE, EMAIL, YTDSALES, LASTINVOICE, LASTCONTACT, BEINGMODIFIED) VALUES"
					+ "(?,?,?,?,?,?,?,?,?,?,?)";
			try {
				PreparedStatement prepStmt = Global.conn.prepareStatement(insertTableSQL);
				prepStmt.setString(1, dataRec.name);
				prepStmt.setString(2, dataRec.address);
				prepStmt.setString(3, dataRec.city);
				prepStmt.setString(4, dataRec.state);
				prepStmt.setString(5, dataRec.zip);
				prepStmt.setString(6, dataRec.phone);
				prepStmt.setString(7, dataRec.email);
				prepStmt.setString(8, dataRec.ytdSales);
				prepStmt.setString(9, dataRec.lastInvNo);
				prepStmt.setString(10, dataRec.lastContact);
				prepStmt.setBoolean(11, dataRec.beingModified);
				// execute insert SQL statement
				prepStmt .executeUpdate();
		    } catch (SQLException sqle) {
				printSQLException(sqle);
		    } finally {
		        if (prepStmt != null) { prepStmt.close(); }
		    }
		}
		
		// This is used by 'Change' mode (on OK) to add the record from the screen into the database
		public void changeRecord () throws SQLException {
			String updateTableSQL = "UPDATE CUSTOMERS SET "
					+ "NAME = ?, "
					+ "ADDRESS = ?,"
					+ "CITY = ?, "
					+ "STATE = ?, "
					+ "ZIP = ?, "	
					+ "PHONE = ?, "
					+ "EMAIL = ?, "
					+ "YTDSALES = ?, "
					+ "LASTINVOICE = ?, "
					+ "LASTCONTACT = ?, "
					+ "BEINGMODIFIED = ? "
					+ "WHERE NAME = ?";
			try {
				PreparedStatement prepStmt = Global.conn.prepareStatement(updateTableSQL);
				prepStmt.setString(1, dataRec.name);
				prepStmt.setString(2, dataRec.address);
				prepStmt.setString(3, dataRec.city);
				prepStmt.setString(4, dataRec.state);
				prepStmt.setString(5, dataRec.zip);
				prepStmt.setString(6, dataRec.phone);
				prepStmt.setString(7, dataRec.email);
				prepStmt.setString(8, dataRec.ytdSales);
				prepStmt.setString(9, dataRec.lastInvNo);
				prepStmt.setString(10, dataRec.lastContact);
				prepStmt.setBoolean(11, false);
				prepStmt.setString(12, Global.saveKey);
				// execute update SQL statement
				prepStmt .executeUpdate();
		    } catch (SQLException sqle) {
				printSQLException(sqle);
		    } finally {
		        if (prepStmt != null) { prepStmt.close(); }
		        // Clear SaveKey here to turn off 'clear' logic in Cancel & Exit
		        Global.saveKey = "";
		    }
		}
		
		// This is used by Delete (on OK) to delte the record
		public void deleteRecord () throws SQLException {
			String updateTableSQL = "DELETE FROM CUSTOMERS "
					+ "WHERE NAME = ?";
			try {
				PreparedStatement prepStmt = Global.conn.prepareStatement(updateTableSQL);
				prepStmt.setString(1, Global.saveKey);
				// execute update SQL statement
				prepStmt .executeUpdate();
		    } catch (SQLException sqle) {
				printSQLException(sqle);
		    } finally {
		        if (prepStmt != null) { prepStmt.close(); }
		        // Clear SaveKey here to turn off 'clear' logic in Cancel & Exit
		        Global.saveKey = "";
		    }
		}
		
		public void clearInUse() throws SQLException {
			if (Global.saveKey == null)
				// Nothing to clear - exit
				return;
			
			String updateTableSQL = "UPDATE CUSTOMERS SET "
					+ "BEINGMODIFIED = false "
					+ "WHERE NAME = ?";
			try {
				PreparedStatement prepStmt = Global.conn.prepareStatement(updateTableSQL);
				prepStmt.setString(1, Global.saveKey);
				// execute update SQL statement
				prepStmt .executeUpdate();
		    } catch (SQLException sqle) {
				printSQLException(sqle);
		    } finally {
		        if (prepStmt != null) { prepStmt.close(); }
		        // Clear SaveKey here to turn off 'clear' logic in Cancel & Exit
		        Global.saveKey = "";
		    }
		}

		
		// This logic is used by both Change & Delete to initially process the record
		public void readRecordForUpdate () throws SQLException {
			// Make sure a record is selected
			if (Global.selectedKey.equals(null)) {
				JOptionPane.showMessageDialog(null, "A Customer must be selected first.");
				Global.messageOccurred = true;
				return;
			}
			// Get the selected record from the database - Key = NAME
			String sql = "SELECT * from CUSTOMERS WHERE NAME = ?";
			try {	
				PreparedStatement prepStmt = Global.conn.prepareStatement(sql);
				prepStmt.setString(1, Global.selectedKey);
				// execute query SQL statement
				ResultSet rs = prepStmt .executeQuery();
				while (rs.next()) {
					dataRec.name          = rs.getString("NAME");
					dataRec.address       = rs.getString("ADDRESS");
					dataRec.city          = rs.getString("CITY");
					dataRec.state         = rs.getString("STATE");
					dataRec.zip           = rs.getString("ZIP");
					dataRec.phone         = rs.getString("PHONE");
					dataRec.email         = rs.getString("EMAIL");
					dataRec.ytdSales      = rs.getString("YTDSALES");
					dataRec.lastInvNo     = rs.getString("LASTINVOICE");
					dataRec.lastContact   = rs.getString("LASTCONTACT");
					dataRec.beingModified = rs.getBoolean("BEINGMODIFIED");
				}
			} catch (SQLException sqle) {
				printSQLException(sqle);
		    } finally {
		    	if (rs != null) { rs.close(); }
		        if (prepStmt != null) { prepStmt.close(); }
		    }
				
			// If record is currently in use - tell user and exit this method
			if (dataRec.beingModified == true) {
				JOptionPane.showMessageDialog(null, "This Customer is being modified by another user.");
				Global.messageOccurred = true;
				return;
			}
			
			// Save off the current key here to use for update, cancel & exit logic
			Global.saveKey = dataRec.name;
			
			// Set the In-Use flag to true for this record so other users cant modify
			String updateTableSQL = "UPDATE CUSTOMERS SET "
					+ "BEINGMODIFIED = true "
					+ "WHERE NAME = ?";
			try {
				PreparedStatement prepStmt = Global.conn.prepareStatement(updateTableSQL);
				prepStmt.setString(1, Global.saveKey);
				// execute update SQL statement
				prepStmt .executeUpdate();
		    } catch (SQLException sqle) {
				printSQLException(sqle);
		    } finally {
		        if (prepStmt != null) { prepStmt.close(); }
		    }
		}

	    // Adds SQL errors to console
	    public void printSQLException(SQLException e) {
	        // Unwraps the entire exception chain to unveil the real cause of the
	        // Exception.
	    	Global.lastSQLState = e.getSQLState();
	    	if (Global.lastSQLState.equals("23505"))
	    		// Duplicate Key on Add or Change - handled in this program
	    		return;
	        while (e != null)
	        {
	            System.err.println("\n----- SQLException -----");
	            System.err.println("  SQL State:  " + e.getSQLState());
	            System.err.println("  Error Code: " + e.getErrorCode());
	            System.err.println("  Message:    " + e.getMessage());
	            // for stack traces, refer to derby.log or uncomment this:
	            //e.printStackTrace(System.err);
	            e = e.getNextException();
	        }
	    }
	}
}				
