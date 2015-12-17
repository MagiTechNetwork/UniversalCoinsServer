package br.com.gamemods.universalcoinsserver.datastore;

import br.com.gamemods.universalcoinsserver.api.UniversalCoinsServerAPI;
import br.com.gamemods.universalcoinsserver.blocks.PlayerOwned;
import br.com.gamemods.universalcoinsserver.tile.TileVendor;
import cpw.mods.fml.common.registry.GameData;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class PropertiesDB implements CardDataBase
{
    private final File baseDir, accounts, players, logs;
    private SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z: ");
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd-HH");

    public PropertiesDB(File baseDir) throws IOException
    {
        this.baseDir = baseDir;
        if(!baseDir.isDirectory() && !baseDir.mkdirs())
            throw new IOException(baseDir.getAbsolutePath()+" is not a directory");

        accounts = createDir(baseDir, "accounts");
        players = createDir(baseDir, "players");
        logs = createDir(baseDir, "logs");
    }

    private File createDir(File base, String name) throws IOException
    {
        File dir = new File(base, name);
        if(!dir.isDirectory() && !dir.mkdirs())
            throw new IOException("Failed to create dir: "+dir.getAbsolutePath());
        return dir;
    }

    private Properties loadProperties(File file) throws DataBaseException
    {
        if(!file.isFile())
            return null;

        Properties properties = new SortedProperties();
        try(FileReader reader = new FileReader(file))
        {
            properties.load(reader);
        }
        catch (Exception e)
        {
            throw new DataBaseException(e);
        }

        return properties;
    }

    private Properties loadPlayer(UUID playerId) throws DataBaseException
    {
        if(playerId == null)
            throw new NullPointerException("playerId");

        File playerFile = new File(players, playerId+".properties");
        Properties playerData;
        if(!playerFile.isFile() || (playerData = loadProperties(playerFile)) == null)
            playerData = new SortedProperties();

        if(!playerData.containsKey("version"))
            playerData.setProperty("version", Integer.toString(Integer.MIN_VALUE));
        if(!playerData.containsKey("id"))
            playerData.setProperty("id", playerId.toString());

        return playerData;
    }

    private Properties loadAccount(String account) throws DataBaseException
    {
        return loadProperties(new File(accounts, account+".properties"));
    }

    private void saveAccount(String account, Properties properties) throws DataBaseException
    {
        File accountFile = new File(accounts, account+".properties");
        try(FileWriter writer = new FileWriter(accountFile))
        {
            properties.store(writer, "Account: "+account);
        }
        catch (Exception e)
        {
            throw new DataBaseException(e);
        }
    }

    @Nonnull
    @Override
    public PlayerData getPlayerData(@Nonnull UUID playerUID) throws DataBaseException
    {
        Properties properties = loadPlayer(playerUID);
        try
        {
            int version = Integer.parseInt(properties.getProperty("version", Integer.toString(Integer.MIN_VALUE)));
            String number = properties.getProperty("account");
            AccountAddress primary;
            List<AccountAddress> alternativeAccounts;
            if(number == null)
                primary = null;
            else
            {
                String[] split = number.split(";", 2);
                primary = new AccountAddress(split[0], split[1], playerUID);
            }

            number = properties.getProperty("alternative.accounts");
            if(number == null)
                alternativeAccounts = null;
            else
            {
                String[] accountSplit = number.split("\\|");
                alternativeAccounts = new ArrayList<>(accountSplit.length);
                for(String str: accountSplit)
                {
                    String[] split = str.split(";",2);
                    alternativeAccounts.add(new AccountAddress(split[0], split[1], playerUID));
                }
            }

            return new PlayerData(version, playerUID, primary, alternativeAccounts);
        }
        catch (Exception e)
        {
            throw new DataBaseException(e);
        }
    }

    private String generateAccountNumber()
    {
        String str = Long.toString((long) (Math.floor(Math.random() * 99999999999L) + 11111111111L));
        return str.substring(0,3)+"."+str.substring(3,6)+"."+str.substring(6,9)+"-"+str.substring(9);
    }

    @Nonnull
    @Override
    public AccountAddress createPrimaryAccount(@Nonnull UUID playerUID, @Nonnull String name) throws DataBaseException
    {
        Properties playerData = loadPlayer(playerUID);
        if(playerData.containsKey("account"))
            throw new DataBaseException("Player "+playerUID+" already have an account: "+playerData.getProperty("account"));

        try
        {
            int version = Integer.parseInt(playerData.getProperty("version", Integer.toString(Integer.MIN_VALUE)));

            String number;
            File file;
            do
            {
                number = generateAccountNumber();
                file = new File(accounts, number+".properties");
            } while (file.exists());

            Properties properties = new SortedProperties();
            properties.setProperty("version", Integer.toString(Integer.MIN_VALUE));
            properties.setProperty("number", number);
            properties.setProperty("owner.id", playerUID.toString());
            properties.setProperty("balance", "0");

            try(FileWriter writer = new FileWriter(file))
            {
                properties.store(writer, "Recently created");
            }

            playerData.setProperty("version", Integer.toString(version+1));
            playerData.setProperty("account", number+";"+name);
            try(FileWriter writer = new FileWriter(new File(players, playerUID+".properties")))
            {
                playerData.store(writer, "Primary account created");
            }

            return new AccountAddress(number, name, playerUID);
        }
        catch (Exception e)
        {
            throw new DataBaseException(e);
        }
    }

    @Override
    public UUID getAccountOwner(String account) throws DataBaseException
    {
        Properties properties = loadAccount(account);
        if (properties == null)
            return null;
        try
        {
            return UUID.fromString(properties.getProperty("owner.id"));
        }
        catch (Exception e)
        {
            throw new DataBaseException(e);
        }
    }

    @Override
    public int getAccountBalance(Object account) throws DataBaseException
    {
        Properties properties = loadAccount(account.toString());
        if(properties == null)
            return -1;

        try
        {
            return Integer.parseInt(properties.getProperty("balance", "0"));
        }
        catch (Exception e)
        {
            throw new DataBaseException(e);
        }
    }

    private int canDeposit(long balance, int deposit)
    {
        balance += deposit;
        balance = Integer.MAX_VALUE - balance;
        if(balance < Integer.MIN_VALUE)
            return Integer.MIN_VALUE;
        else if(balance > Integer.MAX_VALUE)
            return Integer.MAX_VALUE;
        return (int) balance;
    }

    @Override
    public int canDeposit(Object account, int coins) throws DataBaseException
    {
        Properties properties = loadAccount(account.toString());

        try
        {
            return canDeposit(Integer.parseInt(properties.getProperty("balance")), coins);
        }
        catch (Exception e)
        {
            throw new DataBaseException(e);
        }
    }

    @Override
    public int canDeposit(Object account, ItemStack coins) throws DataBaseException
    {
        return canDeposit(account, UniversalCoinsServerAPI.stackValue(coins));
    }

    @Override
    public int canDeposit(Object account, Collection<ItemStack> coins) throws DataBaseException
    {
        return canDeposit(account, UniversalCoinsServerAPI.stackValue(coins));
    }

    @Override
    public int depositToAccount(Object account, ItemStack coins, Transaction transaction) throws DataBaseException
    {
        return depositToAccount(account, Collections.singleton(coins), transaction);
    }

    private int incrementInt(Properties properties, String key, int increment, int defaultValue)
    {
        int current = Integer.parseInt(properties.getProperty(key, Integer.toString(defaultValue)));
        current += increment;
        properties.setProperty(key, Integer.toString(current));
        return current;
    }

    private int incrementInt(Properties properties, String key, int defaultValue)
    {
        return incrementInt(properties, key, 1, defaultValue);
    }

    private File getAccountFile(String account)
    {
        return new File(accounts, account+".properties");
    }

    @Override
    public int takeFromAccount(Object account, int amount, Transaction transaction)
            throws DataBaseException
    {
        Properties properties = loadAccount(account.toString());
        try
        {
            if(amount == 0)
                return Integer.parseInt(properties.getProperty("balance", "0"));
            else if(amount < 0)
                throw new IllegalArgumentException("amount < 0: "+amount);

            int newBalance = incrementInt(properties, "balance", -amount, 0);
            if(newBalance < 0)
                throw new OutOfCoinsException(-newBalance);
            incrementInt(properties, "version", Integer.MIN_VALUE);

            try(FileWriter writer = new FileWriter(getAccountFile(account.toString())))
            {
                properties.store(writer, "Took "+amount+" from balance");;
            }

            try
            {
                saveTransaction(transaction);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            return newBalance;
        }
        catch (DataBaseException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new DataBaseException(e);
        }
    }

    @Override
    public int depositToAccount(Object account, Collection<ItemStack> coinsStacks, Transaction transaction) throws DataBaseException
    {
        int value = UniversalCoinsServerAPI.stackValue(coinsStacks);
        if(value == 0)
            return 0;

        Properties properties = loadAccount(account.toString());
        return deposit(properties, account.toString(), value, transaction);
    }

    private int deposit(Properties properties, String account, int value, Transaction transaction) throws DataBaseException
    {
        if(value == 0)
            return 0;
        else if(value < 0)
            throw new IllegalArgumentException("value < 0: "+value);

        try
        {
            int balance = Integer.parseInt(properties.getProperty("balance"));
            if(canDeposit(balance, value) < 0)
                return 0;

            properties.setProperty("balance", Integer.toString(balance+value));
            incrementInt(properties, "version", Integer.MIN_VALUE);

            try(FileWriter writer = new FileWriter(getAccountFile(account)))
            {
                properties.store(writer, "Balance increased by "+value);
            }

            try
            {
                saveTransaction(transaction);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            return 0;
        }
        catch (Exception e)
        {
            throw new DataBaseException(e);
        }
    }

    @Override
    public int depositToAccount(Object account, int value, Transaction transaction) throws DataBaseException
    {
        Properties properties = loadAccount(account.toString());
        return deposit(properties, account.toString(), value, transaction);
    }

    @Override @Deprecated
    public boolean depositToAccount(String account, int depositAmount, Operator operator, TransactionType transaction, String product) throws DataBaseException
    {
        Properties properties = loadAccount(account);
        if(properties == null)
            return false;

        try
        {
            long balance = Integer.parseInt(properties.getProperty("balance"));
            if(balance < 0)
                return false;

            balance += depositAmount;
            if(balance > Integer.MAX_VALUE)
                return false;

            properties.put("balance", balance);
        }
        catch (Exception e)
        {
            throw new DataBaseException(e);
        }

        saveAccount(account, properties);
        return true;
    }

    private File getMachineLogFile(Machine machine) throws IOException
    {
        File machineDir = createDir(logs, "machine");
        return new File(machineDir, machine.getMachineId()+".log");
    }

    private void addData(StringBuilder sb, Machine machine)
    {
        if(machine instanceof PlayerOwned)
        {
            sb.append(" | PlayerOwner: ").append(((PlayerOwned) machine).getOwnerId());
        }

        if(machine instanceof TileEntity)
        {
            TileEntity te = (TileEntity)machine;
            World worldObj = te.getWorldObj();
            sb.append(" | DIM:").append(worldObj==null?"?":worldObj.provider.dimensionId)
                    .append(" | X:").append(te.xCoord).append(" | Y:").append(te.yCoord).append(" | Z:").append(te.zCoord)
                    .append(" | Block:").append(worldObj==null?"?":GameData.getBlockRegistry().getNameForObject(te.getBlockType()))
                    .append(" | BlockMeta:").append(worldObj==null?"?":te.getBlockMetadata());
        }
    }

    private File getMachineFile(Machine machine) throws IOException
    {
        File file = createDir(baseDir, "machines");
        return new File(file, machine.getMachineId()+".properties");
    }

    private Properties loadMachineProperties(Machine machine) throws DataBaseException
    {
        try
        {
            return loadProperties(getMachineFile(machine));
        }
        catch (Exception e)
        {
            throw new DataBaseException(e);
        }
    }

    private void incrementTransactions(Machine machine, int increment, UUID lastTransaction) throws DataBaseException
    {
        try
        {
            File file = getMachineFile(machine);
            Properties properties = loadMachineProperties(machine);
            if(properties == null)
            {
                saveMachine(machine);
                properties = loadMachineProperties(machine);
                if(properties == null)
                    throw new DataBaseException("Failed to load machine properties: "+machine.getMachineId());
            }

            storeMachine(properties, machine);

            int transactions = Integer.parseInt(properties.getProperty("transactions", "0"));
            properties.setProperty("transactions", Integer.toString(transactions + increment));
            properties.setProperty("transaction.last", lastTransaction.toString());

            try(FileWriter writer = new FileWriter(file))
            {
                normalize(properties);
                properties.store(writer, "Last transaction: "+lastTransaction);
            }
        }
        catch (Exception e)
        {
            throw new DataBaseException(e);
        }
    }

    private  void storeMachine(Properties properties, Machine machine)
    {
        store(properties, "machine", machine);

        TileEntity te = machine.getMachineEntity();
        if(te instanceof TileVendor)
        {
            TileVendor vendor = (TileVendor) te;
            properties.put("vendor.owner.name", String.valueOf(vendor.ownerName));
            properties.put("vendor.coins.owner", vendor.ownerCoins);
            properties.put("vendor.coins.user", vendor.userCoins);
            properties.put("vendor.price", vendor.price);
            properties.put("vendor.infinite", vendor.infinite);
            properties.put("vendor.sell", vendor.sellToUser);
        }
    }

    public void saveMachine(Machine machine) throws DataBaseException
    {
        try
        {
            File file = getMachineFile(machine);
            Properties properties = loadMachineProperties(machine);
            if(properties == null)
            {
                properties = new SortedProperties();
                properties.put("creation", System.currentTimeMillis());
                properties.put("transactions", "0");
            }

            storeMachine(properties, machine);

            try(FileWriter writer = new FileWriter(file))
            {
                normalize(properties);
                properties.store(writer, "");
            }
        }
        catch (Exception e)
        {
            throw new DataBaseException(e);
        }
    }

    @Override
    public void saveNewMachine(@Nonnull Machine machine) throws DataBaseException
    {
        File file;
        try
        {
            file = getMachineLogFile(machine);
        }
        catch (IOException e)
        {
            throw new DataBaseException(e);
        }

        try(FileWriter writer = new FileWriter(file, true))
        {
            StringBuilder sb = new StringBuilder(dateTimeFormat.format(new Date()))
                    .append("Machine created | MachineID:").append(machine.getMachineId());

            addData(sb, machine);

            sb.append("\n");
            writer.write(sb.toString());
        }
        catch (Exception e)
        {
            throw new DataBaseException(e);
        }

        saveMachine(machine);
    }

    @Override
    public void saveTransaction(@Nonnull Transaction transaction) throws DataBaseException
    {
        Machine machine = transaction.getMachine();
        if (machine == null)
            return;

        try
        {
            File file = getMachineLogFile(machine);

            try (FileWriter writer = new FileWriter(file, true))
            {
                StringBuilder sb = new StringBuilder(dateTimeFormat.format(new Date()))
                        .append("Transaction processed")
                        .append(" | TransactionID:").append(transaction.getId());

                addData(sb, machine);

                sb.append(" | TransactionData: ")
                        .append(transaction)
                        .append("\n");


                writer.write(sb.toString());
            }

            File dir = createDir(logs, "transactions");
            Date date = new Date(transaction.getTime());
            dir = createDir(dir, dateFormat.format(date));

            file =  new File(dir, transaction.getId()+".properties");
            Properties properties = new SortedProperties();
            properties.put("id", transaction.getId());
            properties.put("time", transaction.getTime());
            properties.put("operation", transaction.getOperation());
            properties.put("infinite", transaction.isInfiniteMachine());
            properties.put("quantity", transaction.getQuantity());
            properties.put("price", transaction.getPrice());
            properties.put("price.total", transaction.getTotalPrice());
            store(properties, "coins.user", transaction.getUserCoinSource());
            store(properties, "coins.owner", transaction.getOwnerCoinSource());
            store(properties, "operator", transaction.getOperator());
            store(properties, "machine", transaction.getMachine());
            store(properties, "product", transaction.getProduct());
            store(properties, "trade", transaction.getTrade());

            try(FileWriter writer = new FileWriter(file))
            {
                normalize(properties);
                properties.store(writer, "Transaction on "+dateTimeFormat.format(date));
            }

            incrementTransactions(machine, 1, transaction.getId());
        }
        catch (Exception e)
        {
            throw new DataBaseException(e);
        }
    }

    private void normalize(Properties properties)
    {
        HashSet<Object> keys = new HashSet<>(properties.keySet());
        for(Object key: keys)
        {
            Object value = properties.get(key);
            if(!(value instanceof String))
                properties.put(key, String.valueOf(value));
        }
    }

    private void store(Properties properties, String key, Transaction.CoinSource coinSource)
    {
        if(coinSource != null)
        {
            properties.put(key+".balance.before", coinSource.getBalanceBefore());
            properties.put(key+".balance.after", coinSource.getBalanceAfter());
            if(coinSource instanceof Transaction.MachineCoinSource)
            {
                properties.put(key+".type", "machine");
                properties.put(key+".machine.id", ((Transaction.MachineCoinSource) coinSource).getMachine().getMachineId());
            }
            else if(coinSource instanceof Transaction.CardCoinSource)
            {
                properties.put(key+".type", "card");
                Transaction.CardCoinSource card = (Transaction.CardCoinSource) coinSource;
                AccountAddress accountAddress = card.getAccountAddress();
                properties.put(key+".account.number", accountAddress.getNumber());
                properties.put(key+".account.owner", accountAddress.getOwner());
                properties.put(key+".account.name", accountAddress.getName());
                store(properties, key+".card", card.getCard());
            }
            else if(coinSource instanceof Transaction.InventoryCoinSource)
            {
                properties.put(key+".type", "inventory");
                store(properties, key+".holder", ((Transaction.InventoryCoinSource) coinSource).getOperator());
            }
        }
    }

    private void store(Properties properties, String key, Operator operator)
    {
        if(operator != null)
        {
            if(operator instanceof PlayerOperator)
            {
                properties.put(key+".type", "player");
                properties.put(key+".player", ((PlayerOperator) operator).getPlayerId());
            }
            else if(operator instanceof BlockOperator)
            {
                if(operator instanceof MachineOperator)
                {
                    properties.put(key+".type", "machine");
                    properties.put(key+".machine.id", ((MachineOperator) operator).getMachine().getMachineId());
                }
                else
                {
                    properties.put(key+".type", "block");
                }
                BlockOperator bo = (BlockOperator) operator;
                if(bo.getOwner() != null)
                    properties.put(key+".owner", bo.getOwner());

                properties.put(key+".block.x", bo.getX());
                properties.put(key+".block.y", bo.getY());
                properties.put(key+".block.z", bo.getZ());
                properties.put(key+".block.dim", bo.getDim());
                properties.put(key+".block.meta", bo.getBlockMeta());
                properties.put(key+".block.id", bo.getBlockId());
            }
        }
    }

    private void store(Properties properties, String key, Machine machine)
    {
        if(machine != null)
        {
            properties.put(key+".id", machine.getMachineId());
            if(machine instanceof PlayerOwned)
            {
                UUID ownerId = ((PlayerOwned) machine).getOwnerId();
                properties.put(key+".owner", String.valueOf(ownerId));
            }

            TileEntity te = machine.getMachineEntity();
            if(te != null)
            {
                World worldObj = te.getWorldObj();
                properties.put(key+".tile.dim", worldObj==null?"null":worldObj.provider.dimensionId);
                properties.put(key+".tile.x", te.xCoord);
                properties.put(key+".tile.y", te.yCoord);
                properties.put(key+".tile.z", te.zCoord);
                properties.put(key+".tile.block", String.valueOf(GameData.getBlockRegistry().getNameForObject(te.getBlockType())));
                properties.put(key+".tile.block.meta", te.getBlockMetadata());
            }
        }
    }

    private void store(Properties properties, String key, ItemStack stack)
    {
        if(stack != null)
        {
            properties.put(key, String.valueOf(stack));
            properties.put(key+".type", String.valueOf(GameData.getItemRegistry().getNameForObject(stack.getItem())));
            properties.put(key+".meta", stack.getItemDamage());
            properties.put(key+".amount", stack.stackSize);
            if(stack.hasTagCompound())
                properties.put(key+".tags", stack.stackTagCompound.toString());
        }
    }

    static class SortedProperties extends Properties
    {
        @SuppressWarnings("unchecked")
        public Enumeration keys()
        {
            Enumeration<Object> keysEnum = super.keys();
            Vector<String> keyList = new Vector<>();
            while(keysEnum.hasMoreElements())
            {
                Object object = keysEnum.nextElement();
                keyList.add(String.valueOf(object));
            }
            Collections.sort(keyList);
            return keyList.elements();
        }
    }
}
