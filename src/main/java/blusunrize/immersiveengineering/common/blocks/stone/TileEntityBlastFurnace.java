package blusunrize.immersiveengineering.common.blocks.stone;

import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.IEProperties.PropertyBoolInverted;
import blusunrize.immersiveengineering.api.crafting.BlastFurnaceRecipe;
import blusunrize.immersiveengineering.common.IEContent;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IActiveState;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IGuiTile;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IUsesBooleanProperty;
import blusunrize.immersiveengineering.common.blocks.TileEntityMultiblockPart;
import blusunrize.immersiveengineering.common.util.Utils;
import blusunrize.immersiveengineering.common.util.inventory.IIEInventory;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.oredict.OreDictionary;

public class TileEntityBlastFurnace extends TileEntityMultiblockPart<TileEntityBlastFurnace> implements IIEInventory, IActiveState, IGuiTile
{
	ItemStack[] inventory = new ItemStack[4];
	public int process = 0;
	public int processMax = 0;
	public boolean active = false;
	public int burnTime = 0;
	public int lastBurnTime = 0;

	@Override
	public PropertyBoolInverted getBoolProperty(Class<? extends IUsesBooleanProperty> inf)
	{
		return inf==IActiveState.class?IEProperties.BOOLEANS[0]:null;
	}
	@Override
	public boolean getIsActive()
	{
		return this.active;
	}
	@Override
	public boolean canOpenGui()
	{
		return formed;
	}
	@Override
	public int getGuiID()
	{
		return Lib.GUIID_BlastFurnace;
	}
	@Override
	public TileEntity getGuiMaster()
	{
		TileEntity master = this.master();
		return master!=null?master:this;
	}

	@Override
	public float[] getBlockBounds()
	{
		return new float[]{0,0,0,1,1,1};
	}
	@Override
	public float[] getSpecialCollisionBounds()
	{
		return null;
	}
	@Override
	public float[] getSpecialSelectionBounds()
	{
		return null;
	}

	@Override
	public ItemStack getOriginalBlock()
	{
		return new ItemStack(IEContent.blockStoneDecoration,1,1);
	}

	@Override
	public boolean isDummy()
	{
		return offset[0]!=0||offset[1]!=0||offset[2]!=0;
	}

	@Override
	public void update()
	{
		if(!worldObj.isRemote&&formed&&!isDummy())
		{
			boolean a = active;

			if(burnTime>0)
			{			
				if(process>0)
				{
					int processSpeed = getProcessSpeed();
					if(inventory[0]==null)
					{
						process=0;
						processMax=0;
					}
					else
					{
						process-=processSpeed;
						if(!active)
							active=true;
					}
					burnTime-=processSpeed;
					worldObj.markBlockForUpdate(getPos());
				}

				if(process<=0)
				{
					if(active)
					{
						BlastFurnaceRecipe recipe = getRecipe();
						if(recipe!=null)
						{
							Utils.modifyInvStackSize(inventory, 0, -(recipe.input instanceof ItemStack?((ItemStack)recipe.input).stackSize:1));
							if(inventory[2]!=null)
								inventory[2].stackSize+=recipe.output.copy().stackSize;
							else
								inventory[2] = recipe.output.copy();
							if (recipe.slag!=null)
							{
								if(inventory[3]!=null)
									inventory[3].stackSize+=recipe.slag.copy().stackSize;
								else
									inventory[3] = recipe.slag.copy();
							}
						}
						processMax=0;
						active=false;
					}
					BlastFurnaceRecipe recipe = getRecipe();
					if(recipe!=null)
					{
						this.process=recipe.time;
						this.processMax=process;
						this.active=true;
					}
				}
			}
			else
			{
				if(active)
					active=false;
			}

			if(burnTime<=10 && getRecipe()!=null)
			{
				if(BlastFurnaceRecipe.isValidBlastFuel(inventory[1]))
				{
					burnTime += BlastFurnaceRecipe.getBlastFuelTime(inventory[1]);
					lastBurnTime = BlastFurnaceRecipe.getBlastFuelTime(inventory[1]);
					Utils.modifyInvStackSize(inventory, 1, -1);
					worldObj.markBlockForUpdate(getPos());
				}
			}

			if(a!=active)
			{

				this.markDirty();
				TileEntity tileEntity;
				for(int yy=-1;yy<=1;yy++)
					for(int xx=-1;xx<=1;xx++)
						for(int zz=-1;zz<=1;zz++)
						{
							tileEntity = worldObj.getTileEntity(getPos().add(xx, yy, zz));
							if(tileEntity!=null)
								tileEntity.markDirty();
							worldObj.markBlockForUpdate(getPos().add(xx, yy, zz));
							worldObj.addBlockEvent(getPos().add(xx, yy, zz), IEContent.blockStoneDevice, 1,active?1:0);
						}
			}
		}
	}
	public BlastFurnaceRecipe getRecipe()
	{
		BlastFurnaceRecipe recipe = BlastFurnaceRecipe.findRecipe(inventory[0]);
		if(recipe==null)
			return null;
		if((inventory[0].stackSize>=((recipe.input instanceof ItemStack)?((ItemStack)recipe.input).stackSize:1)
				&& inventory[2]==null || (OreDictionary.itemMatches(inventory[2],recipe.output,true) && inventory[2].stackSize+recipe.output.stackSize<=getSlotLimit(2)) )
				&& (inventory[3]==null || (OreDictionary.itemMatches(inventory[3],recipe.slag,true) && inventory[3].stackSize+recipe.slag.stackSize<=getSlotLimit(3)) ))
			return recipe;
		return null;
	}

	protected int getProcessSpeed()
	{
		return 1;
	}

	@Override
	public boolean receiveClientEvent(int id, int arg)
	{
		if(id==0)
			this.formed = arg==1;
		else if(id==1)
			this.active = arg==1;
		markDirty();
		worldObj.markBlockForUpdate(getPos());
		return true;
	}

	@Override
	public void readCustomNBT(NBTTagCompound nbt, boolean descPacket)
	{
		super.readCustomNBT(nbt, descPacket);
		process = nbt.getInteger("process");
		processMax = nbt.getInteger("processMax");
		active = nbt.getBoolean("active");
		burnTime = nbt.getInteger("burnTime");
		lastBurnTime = nbt.getInteger("lastBurnTime");
		if(!descPacket)
		{
			inventory = Utils.readInventory(nbt.getTagList("inventory", 10), 4);
		}
	}

	@Override
	public void writeCustomNBT(NBTTagCompound nbt, boolean descPacket)
	{
		super.writeCustomNBT(nbt, descPacket);
		nbt.setInteger("process", process);
		nbt.setInteger("processMax", processMax);
		nbt.setBoolean("active", active);
		nbt.setInteger("burnTime", burnTime);
		nbt.setInteger("lastBurnTime", lastBurnTime);
		if(!descPacket)
		{
			nbt.setTag("inventory", Utils.writeInventory(inventory));
		}
	}

	@Override
	public void disassemble()
	{
		if(formed && !worldObj.isRemote)
		{
			BlockPos startPos = this.getPos().add(-offset[0],-offset[1],-offset[2]);
			if(!(offset[0]==0&&offset[1]==0&&offset[2]==0) && !(worldObj.getTileEntity(startPos) instanceof TileEntityBlastFurnace))
				return;

			for(int yy=-1;yy<=1;yy++)
				for(int xx=-1;xx<=1;xx++)
					for(int zz=-1;zz<=1;zz++)
					{
						ItemStack s = null;
						TileEntity te = worldObj.getTileEntity(startPos.add(xx, yy, zz));
						if(te instanceof TileEntityBlastFurnace)
						{
							s = ((TileEntityBlastFurnace)te).getOriginalBlock();
							((TileEntityBlastFurnace)te).formed=false;
						}
						if(startPos.add(xx, yy, zz).equals(getPos()))
							s = this.getOriginalBlock();
						if(s!=null && Block.getBlockFromItem(s.getItem())!=null)
						{
							if(startPos.add(xx, yy, zz).equals(getPos()))
								worldObj.spawnEntityInWorld(new EntityItem(worldObj, getPos().getX()+.5,getPos().getY()+.5,getPos().getZ()+.5, s));
							else
							{
								if(Block.getBlockFromItem(s.getItem())==IEContent.blockStoneDevice)
									worldObj.setBlockToAir(startPos.add(xx, yy, zz));
								worldObj.setBlockState(startPos.add(xx, yy, zz), Block.getBlockFromItem(s.getItem()).getStateFromMeta(s.getItemDamage()));
							}
						}
					}
		}
	}

	@Override
	public ItemStack[] getInventory()
	{
		return this.inventory;
	}
	@Override
	public boolean isStackValid(int slot, ItemStack stack)
	{
		return slot==0?BlastFurnaceRecipe.findRecipe(stack)!=null: slot==1?BlastFurnaceRecipe.isValidBlastFuel(stack): false;
	}
	@Override
	public int getSlotLimit(int slot)
	{
		return 64;
	}
	@Override
	public void doGraphicalUpdates(int slot)
	{
	}
	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing facing)
	{
		if(capability==net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
			return null;
		return super.getCapability(capability, facing);
	}
}