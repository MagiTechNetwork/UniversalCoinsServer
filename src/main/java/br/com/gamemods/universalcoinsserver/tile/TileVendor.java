package br.com.gamemods.universalcoinsserver.tile;

import br.com.gamemods.universalcoinsserver.UniversalCoinsServer;
import br.com.gamemods.universalcoinsserver.blocks.PlayerOwned;
import br.com.gamemods.universalcoinsserver.datastore.*;
import br.com.gamemods.universalcoinsserver.item.ItemCoin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TileVendor extends TileEntity implements IInventory, PlayerOwned
{
    public static final int SLOT_STORAGE_FIST = 0;
    public static final int SLOT_STORAGE_LAST = 8;
    public static final int SLOT_TRADE = 9;
    public static final int SLOT_OWNER_CARD = 10;
    public static final int SLOT_SELL = 11;
    public static final int SLOT_OUTPUT = 12;
    public static final int SLOT_COIN_OUTPUT = 13;
    public static final int SLOT_OWNER_COIN_INPUT = 14;
    public static final int SLOT_USER_COIN_INPUT = 15;
    public static final int SLOT_USER_CARD = 16;
    public static final int BUTTON_MODE = 0;
    public static final int BUTTON_COLOR_MINUS = 15;
    public static final int BUTTON_COLOR_PLUS = 16;

    private ItemStack[] inventory = new ItemStack[17];

    public String ownerName;
    public UUID owner;
    public int ownerCoins;
    public int userCoins;
    public int price;
    public boolean infinite;
    public boolean sellMode;
    public byte textColor;
    public EntityPlayer opener;

    public void validateFields()
    {
        if(ownerCoins < 0) ownerCoins = 0;
        if(userCoins < 0) userCoins = 0;
        if(price < 0) price = 0;
    }

    @Override
    public void writeToNBT(NBTTagCompound compound)
    {
        super.writeToNBT(compound);

        validateFields();

        NBTTagList itemList = new NBTTagList();
        for(int i=0; i < inventory.length; i++)
        {
            ItemStack stack = inventory[i];
            if(stack != null)
            {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setByte("Slot", (byte) i);
                stack.writeToNBT(tag);
                itemList.appendTag(tag);
            }
        }

        compound.setTag("Inventory", itemList);
        compound.setInteger("CoinSum", ownerCoins);
        compound.setInteger("UserCoinSum", userCoins);
        compound.setInteger("ItemPrice", price);
        if(ownerName != null && !ownerName.isEmpty())
            compound.setString("OwnerName", ownerName);
        if(owner != null)
            compound.setString("BlockOwner", owner.toString());
        compound.setBoolean("Infinite", infinite);
        compound.setBoolean("Mode", sellMode);
        compound.setBoolean("OutOfStock", false);
        compound.setBoolean("OutOfCoins", false);
        compound.setBoolean("InventoryFull", false);
        compound.setBoolean("BuyButtonActive", false);
        compound.setBoolean("SellButtonActive", false);
        compound.setBoolean("CoinButtonActive", false);
        compound.setBoolean("SmallStackButtonActive", false);
        compound.setBoolean("LargeStackButtonActive", false);
        compound.setBoolean("SmallBagButtonActive", false);
        compound.setBoolean("LargeBagButtonActive", false);
        compound.setBoolean("UserCoinButtonActive", false);
        compound.setBoolean("UserSmallStackButtonActive", false);
        compound.setBoolean("UserLargeStackButtonActive", false);
        compound.setBoolean("UserSmallBagButtonActive", false);
        compound.setBoolean("UserLargeBagButtonActive", false);
        compound.setBoolean("InUse", opener != null);
        compound.setString("BlockIcon", "");
        compound.setInteger("TextColor", textColor);
        compound.setInteger("remoteX", 0);
        compound.setInteger("remoteY", 0);
        compound.setInteger("remoteZ", 0);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound)
    {
        super.readFromNBT(compound);

        NBTTagList tagList = compound.getTagList("Inventory", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tagList.tagCount(); i++)
        {
            NBTTagCompound tag = tagList.getCompoundTagAt(i);
            byte slot = tag.getByte("Slot");
            if (slot >= 0 && slot < inventory.length)
            {
                inventory[slot] = ItemStack.loadItemStackFromNBT(tag);
            }
        }

        ownerName = compound.getString("OwnerName");
        String str = compound.getString("BlockOwner");
        if(str.isEmpty()) owner = null;
        else
            try
            {
                owner = UUID.fromString(str);
            }
            catch (Exception e)
            {
                owner = null;
            }

        ownerCoins = compound.getInteger("CoinSum");
        userCoins = compound.getInteger("UserCoinSum");
        price = compound.getInteger("ItemPrice");
        infinite = compound.getBoolean("Infinite");
        sellMode = compound.getBoolean("Mode");
        textColor = (byte) compound.getInteger("TextColor");

        validateFields();
    }

    @Override
    public Packet getDescriptionPacket()
    {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        NBTTagList tagList = nbt.getTagList("Inventory", Constants.NBT.TAG_COMPOUND);
        List<Byte> slots = new ArrayList<Byte>(inventory.length);
        for (int i = 0; i < tagList.tagCount(); i++)
        {
            NBTTagCompound tag = tagList.getCompoundTagAt(i);
            byte slot = tag.getByte("Slot");
            slots.add(slot);
        }
        ItemStack stack = new ItemStack(Blocks.air, 0);
        for(byte i = 0; i < inventory.length; i++)
        {
            if(!slots.contains(i))
            {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setByte("Slot", i);
                stack.writeToNBT(tag);
                tagList.appendTag(tag);
            }
        }
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
    }

    @Override
    public int getSizeInventory()
    {
        return inventory.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot)
    {
        return inventory[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int size)
    {
        ItemStack stack = getStackInSlot(slot);
        if (stack != null)
        {
            if (stack.stackSize <= size)
            {
                setInventorySlotContents(slot, null);
            }
            else
            {
                stack = stack.splitStack(size);
                if (stack.stackSize == 0)
                {
                    setInventorySlotContents(slot, null);
                }
            }
        }

        scheduleUpdate();

        return stack;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot)
    {
        opener = null;
        return getStackInSlot(slot);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack)
    {
        inventory[slot] = stack;
        scheduleUpdate();

        if(stack == null)
            return;

        Item item = stack.getItem();
        switch (slot)
        {
            case SLOT_OWNER_COIN_INPUT:
            case SLOT_USER_COIN_INPUT:
            {
                if(item instanceof ItemCoin)
                {
                    int current;
                    int cardSlot;
                    boolean owner = SLOT_OWNER_COIN_INPUT == slot;
                    if(owner)
                    {
                        current = ownerCoins;
                        cardSlot = SLOT_OWNER_CARD;
                    }
                    else
                    {
                        current = userCoins;
                        cardSlot = SLOT_USER_CARD;
                    }

                    int itemValue = ((ItemCoin)item).getValue();
                    int depositAmount = Math.min(stack.stackSize, (Integer.MAX_VALUE - current) / itemValue);

                    int depositValue = depositAmount * itemValue;

                    if (!checkEnderCardAcceptDeposit(cardSlot, depositValue)
                            || !depositToEnderCard(cardSlot, depositValue))
                    {
                        current += depositValue;
                    }

                    if(owner) ownerCoins = current;
                    else userCoins = current;

                    inventory[slot].stackSize -= depositAmount;
                    if (inventory[slot].stackSize == 0)
                        inventory[slot] = null;
                }
                break;
            }
        }
    }

    private boolean checkEnderCardAcceptDeposit(int cardSlot, int depositAmount)
    {
        ItemStack stack = inventory[cardSlot];
        if(stack == null || !stack.hasTagCompound())
            return false;

        Item item = stack.getItem();
        if(item != UniversalCoinsServer.proxy.itemEnderCard)
            return false;

        UUID owner;
        if(cardSlot == SLOT_OWNER_CARD)
            owner = this.owner;
        else if(cardSlot == SLOT_USER_CARD)
        {
            if(opener == null) return false;
            owner = opener.getPersistentID();
        }
        else
            return false;

        String account = stack.getTagCompound().getString("Account");
        if(account.isEmpty())
            return false;

        UUID cardOwner;
        try
        {
            cardOwner = UniversalCoinsServer.cardDb.getAccountOwner(account);
        } catch (DataBaseException e)
        {
            UniversalCoinsServer.logger.warn(e);
            return false;
        }

        if(!owner.equals(cardOwner))
            return false;

        int balance;
        try
        {
            balance = UniversalCoinsServer.cardDb.getAccountBalance(account);
        } catch (DataBaseException e)
        {
            UniversalCoinsServer.logger.warn(e);
            return false;
        }
        return balance >= 0 && ((long)depositAmount)+balance < Integer.MAX_VALUE;
    }

    private boolean depositToEnderCard(int cardSlot, int depositAmount)
    {
        CardOperator operator;
        if(cardSlot == SLOT_USER_CARD)
        {
            if(opener != null) operator = new PlayerOperator(opener);
            else operator = new BlockOperator(this);
        }
        else if(cardSlot == SLOT_OWNER_CARD)
        {
            if(opener != null && opener.getPersistentID().equals(owner)) operator = new PlayerOperator(opener);
            else operator = new BlockOperator(this);
        }
        else
            return false;

        return depositToEnderCard(cardSlot, depositAmount, operator, TransactionType.DEPOSIT_FROM_MACHINE, null);
    }

    private boolean depositToEnderCard(int cardSlot, int depositAmount, CardOperator operator, TransactionType transaction, String product)
    {
        ItemStack stack = inventory[cardSlot];
        if(stack == null || !stack.hasTagCompound())
            return false;

        Item item = stack.getItem();
        if(item != UniversalCoinsServer.proxy.itemEnderCard)
            return false;

        String account = stack.getTagCompound().getString("Account");
        if(account.isEmpty())
            return false;

        try
        {
            return UniversalCoinsServer.cardDb.depositToAccount(account, depositAmount, operator, transaction, product);
        } catch (DataBaseException e)
        {
            UniversalCoinsServer.logger.warn(e);
            return false;
        }
    }

    @Override
    public String getInventoryName()
    {
        return null;
    }

    @Override
    public boolean hasCustomInventoryName()
    {
        return false;
    }

    @Override
    public int getInventoryStackLimit()
    {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player)
    {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
                && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) < 64;
    }

    @Override
    public void openInventory()
    {

    }

    @Override
    public void closeInventory()
    {

    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack)
    {
        return false;
    }

    public void scheduleUpdate()
    {
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public void updateBlocks()
    {

    }

    public void onButtonPressed(EntityPlayerMP player, int buttonId, boolean shiftPressed)
    {
        // Security check
        switch (buttonId)
        {
            case BUTTON_MODE:
            case BUTTON_COLOR_MINUS:
            case BUTTON_COLOR_PLUS:
                if(!player.getPersistentID().equals(owner))
                    return;
        }

        // Action
        switch (buttonId)
        {
            case BUTTON_MODE:
                onModeButtonPressed();
                return;

            case BUTTON_COLOR_MINUS:
                if(textColor > 0)
                    textColor--;
                else
                    textColor = 15;
                updateBlocks();
                scheduleUpdate();
                return;

            case BUTTON_COLOR_PLUS:
                if(textColor < 15)
                    textColor++;
                else
                    textColor = 0;
                updateBlocks();
                scheduleUpdate();
        }
    }

    public void onModeButtonPressed()
    {
        sellMode = !sellMode;
        updateBlocks();
    }

    public boolean isInUse(EntityPlayer player)
    {
        if(opener == null)
            return false;

        if(!opener.isEntityAlive() || !isUseableByPlayer(opener))
        {
            opener = null;
            return false;
        }

        return !opener.isEntityEqual(player);

    }

    public void onContainerClosed(EntityPlayer player)
    {
        if(player.isEntityEqual(opener))
            opener = null;
    }

    @Override
    public UUID getOwnerId()
    {
        return owner;
    }
}
