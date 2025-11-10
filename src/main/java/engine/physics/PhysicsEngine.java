package engine.physics;

import engine.world.World;
import engine.world.block.BlockType;
import engine.world.Chunk;
import engine.world.AbstractBlock;
import engine.rendering.Camera;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

public class PhysicsEngine {
	private final World world;
	private final Camera camera;

	private static final float PLAYER_WIDTH = 0.6f;
	private static final float PLAYER_DEPTH = 0.6f;
	private static final float PLAYER_HEIGHT = 1.8f;
	private static final float CROUCH_HEIGHT = 1.0f;
	private static final float EYE_HEIGHT = 1.62f;

	private float velocityY = 0f;
	private boolean isOnGround = false;
	private boolean crouching = false;
	private boolean jumpedThisTick = false;
	private static final float GRAVITY = -10f;
	private static final float JUMP_VELOCITY = 5.5f;
	private static final float GRAVITY_WATER = -2.0f;
	private static final float SWIM_UP_ACCEL = 6.0f;
	private static final float SWIM_VY_UP_MAX = 3.0f;
	private static final float SWIM_VY_DN_MAX = -1.3f;
	private static final float STEP_HEIGHT = 0.5f;
	private float pendingStepRise = 0f;

	public float consumeStepRise() {
		float r = pendingStepRise;
		pendingStepRise = 0f;
		return r;
	}

	private boolean allowStepUp() {
		return isOnGround && !jumpedThisTick && velocityY <= 1e-4f;
	}

	public PhysicsEngine(World world, Camera camera) {
		this.world = world;
		this.camera = camera;
	}

	public boolean isOnGround() {
		return isOnGround;
	}

	public void update(float delta, boolean jumpPressed, boolean crouchPressed) {
		crouching = crouchPressed;
		final float playerHeight = crouching ? CROUCH_HEIGHT : PLAYER_HEIGHT;
		Submersion sub = computeSubmersion(camera.getPosition());
		boolean inWater = sub.ratio > 0f;
		if (!inWater) {
			if (jumpPressed && isOnGround) {
				velocityY = JUMP_VELOCITY;
				isOnGround = false;
				jumpedThisTick = true;
			} else {
				jumpedThisTick = false;
			}
			velocityY += GRAVITY * delta;
		} else {
			jumpedThisTick = jumpPressed;
			if (jumpPressed) {
				velocityY += SWIM_UP_ACCEL * delta;
			} else {
				velocityY += GRAVITY_WATER * delta;
			}
			if (velocityY > SWIM_VY_UP_MAX)
				velocityY = SWIM_VY_UP_MAX;
			if (velocityY < SWIM_VY_DN_MAX)
				velocityY = SWIM_VY_DN_MAX;
		}
		Vector3f pos = camera.getPosition();
		AABB player = playerAABB(pos.x, pos.y, pos.z, playerHeight);
		float dy = velocityY * delta;
		List<AABB> colliders = collectNearbyColliders(player, 0f, dy, 0f);
		float resolvedDy = collideY(player, colliders, dy);
		if (resolvedDy != dy) {
			if (dy < 0) {
				isOnGround = true;
			}
			velocityY = 0f;
		} else {
			isOnGround = false;
		}
		player = player.offset(0f, resolvedDy, 0f);
		pos.y = player.minY;
	}

	public float resolveHorizontalX(float dx, float playerHeight) {
		Vector3f pos = camera.getPosition();
		AABB player = playerAABB(pos.x, pos.y, pos.z, playerHeight);
		List<AABB> colliders = collectNearbyColliders(player, dx, 0f, 0f);
		float resolvedDx = collideX(player, colliders, dx);
		if (Math.abs(resolvedDx - dx) > 1e-6f && STEP_HEIGHT > 0f && allowStepUp()) {
			float stepRise = tryStepUp(player, colliders, dx, 0f, STEP_HEIGHT);
			if (stepRise > 0f) {
				pendingStepRise = Math.max(pendingStepRise, stepRise);
				AABB raised = player.offset(0f, stepRise, 0f);
				List<AABB> col2 = collectNearbyColliders(raised, dx, 0f, 0f);
				resolvedDx = collideX(raised, col2, dx);
			}
		}
		return resolvedDx;
	}

	public float resolveHorizontalZ(float dz, float playerHeight) {
		Vector3f pos = camera.getPosition();
		AABB player = playerAABB(pos.x, pos.y, pos.z, playerHeight);
		List<AABB> colliders = collectNearbyColliders(player, 0f, 0f, dz);
		float resolvedDz = collideZ(player, colliders, dz);
		if (Math.abs(resolvedDz - dz) > 1e-6f && STEP_HEIGHT > 0f && allowStepUp()) {
			float stepRise = tryStepUp(player, colliders, 0f, dz, STEP_HEIGHT);
			if (stepRise > 0f) {
				pendingStepRise = Math.max(pendingStepRise, stepRise);
				AABB raised = player.offset(0f, stepRise, 0f);
				List<AABB> col2 = collectNearbyColliders(raised, 0f, 0f, dz);
				resolvedDz = collideZ(raised, col2, dz);
			}
		}
		return resolvedDz;
	}

	public boolean canMoveToX(float newX) {
		Vector3f pos = camera.getPosition();
		float h = crouching ? CROUCH_HEIGHT : PLAYER_HEIGHT;
		float dx = newX - pos.x;
		AABB player = playerAABB(pos.x, pos.y, pos.z, h);
		List<AABB> colliders = collectNearbyColliders(player, dx, 0f, 0f);
		float resolved = collideX(player, colliders, dx);
		return Math.abs(resolved - dx) < 1e-6f;
	}

	public boolean canMoveToZ(float newZ) {
		Vector3f pos = camera.getPosition();
		float h = crouching ? CROUCH_HEIGHT : PLAYER_HEIGHT;
		float dz = newZ - pos.z;
		AABB player = playerAABB(pos.x, pos.y, pos.z, h);
		List<AABB> colliders = collectNearbyColliders(player, 0f, 0f, dz);
		float resolved = collideZ(player, colliders, dz);
		return Math.abs(resolved - dz) < 1e-6f;
	}

	private AABB playerAABB(float x, float y, float z, float height) {
		float halfW = PLAYER_WIDTH * 0.5f;
		float halfD = PLAYER_DEPTH * 0.5f;
		return new AABB(x - halfW, y, z - halfD, x + halfW, y + height, z + halfD);
	}

	private List<AABB> collectNearbyColliders(AABB player, float dx, float dy, float dz) {
		final float PAD = 0.001f;
		float minX = Math.min(player.minX, player.minX + dx) - PAD;
		float minY = Math.min(player.minY, player.minY + dy) - PAD;
		float minZ = Math.min(player.minZ, player.minZ + dz) - PAD;
		float maxX = Math.max(player.maxX, player.maxX + dx) + PAD;
		float maxY = Math.max(player.maxY, player.maxY + dy) + PAD;
		float maxZ = Math.max(player.maxZ, player.maxZ + dz) + PAD;
		int x0 = (int) Math.floor(minX);
		int y0 = (int) Math.floor(minY);
		int z0 = (int) Math.floor(minZ);
		int x1 = (int) Math.floor(maxX);
		int y1 = (int) Math.floor(maxY);
		int z1 = (int) Math.floor(maxZ);
		ArrayList<AABB> out = new ArrayList<>();
		for (int y = y0; y <= y1; y++) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					AbstractBlock b = getBlockAt(world, x, y, z);
					if (b == null)
						continue;
					BlockType t = b.getType();
					if (t == null || t == BlockType.AIR || t == BlockType.WATER)
						continue;
					List<AABB> boxes = b.getCollisionBoxes();
					if (boxes == null || boxes.isEmpty())
						continue;
					for (AABB local : boxes)
						out.add(local.offset(x, y, z));
				}
			}
		}
		return out;
	}

	private float collideX(AABB player, List<AABB> colliders, float dx) {
		float out = dx;
		for (AABB c : colliders)
			out = c.collideX(player, out);
		return out;
	}

	private float collideY(AABB player, List<AABB> colliders, float dy) {
		float out = dy;
		for (AABB c : colliders)
			out = c.collideY(player, out);
		return out;
	}

	private float collideZ(AABB player, List<AABB> colliders, float dz) {
		float out = dz;
		for (AABB c : colliders)
			out = c.collideZ(player, out);
		return out;
	}

	private float tryStepUp(AABB player, List<AABB> ignored, float dx, float dz, float maxStep) {
		final float stepInc = 0.05f;
		float climbed = 0f;
		while (climbed < maxStep) {
			climbed += stepInc;
			AABB raised = player.offset(0f, climbed, 0f);
			List<AABB> col = collectNearbyColliders(raised, dx, 0f, dz);
			float dyFree = collideY(player, col, climbed);
			if (Math.abs(dyFree - climbed) > 1e-4f)
				continue;
			float support = collideY(raised, col, -0.25f);
			boolean hasSupport = (support < 0f) && (Math.abs(support) < 1.25f - 1e-4f);
			if (!hasSupport)
				continue;
			float dxFree = collideX(raised, col, dx);
			float dzFree = collideZ(raised, col, dz);
			if (Math.abs(dxFree - dx) < 1e-4f && Math.abs(dzFree - dz) < 1e-4f) {
				System.out.println(climbed);
				return climbed;
			}
		}
		return 0f;
	}

	private static final class Submersion {
		boolean feet, torso, head;
		float ratio;
	}

	private Submersion computeSubmersion(Vector3f pos) {
		int gx = (int) Math.floor(pos.x);
		int gz = (int) Math.floor(pos.z);
		int yFeet = (int) Math.floor(pos.y);
		int yTorso = (int) Math.floor(pos.y + EYE_HEIGHT * 0.5f);
		int yHead = (int) Math.floor(pos.y + EYE_HEIGHT);
		Submersion s = new Submersion();
		s.feet = isWaterAt(gx, yFeet, gz);
		s.torso = isWaterAt(gx, yTorso, gz);
		s.head = isWaterAt(gx, yHead, gz);
		int count = (s.feet ? 1 : 0) + (s.torso ? 1 : 0) + (s.head ? 1 : 0);
		s.ratio = count / 3.0f;
		return s;
	}

	private boolean isWaterAt(int x, int y, int z) {
		AbstractBlock b = world.getBlock(x, y, z);
		return b != null && b.getType() == BlockType.WATER;
	}

	private AbstractBlock getBlockAt(World world, int x, int y, int z) {
		int chunkX = Math.floorDiv(x, Chunk.SIZE);
		int chunkZ = Math.floorDiv(z, Chunk.SIZE);
		int localX = Math.floorMod(x, Chunk.SIZE);
		int localY = y;
		int localZ = Math.floorMod(z, Chunk.SIZE);
		Chunk chunk = world.getChunk(chunkX, chunkZ);
		if (chunk == null || localX < 0 || localX >= Chunk.SIZE || localY < 0 || localY >= Chunk.HEIGHT || localZ < 0
				|| localZ >= Chunk.SIZE)
			return null;
		return chunk.getBlock(localX, localY, localZ);
	}

	public static final class AABB {
		public final float minX, minY, minZ;
		public final float maxX, maxY, maxZ;

		public AABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
			this.minX = minX;
			this.minY = minY;
			this.minZ = minZ;
			this.maxX = maxX;
			this.maxY = maxY;
			this.maxZ = maxZ;
		}

		public AABB offset(float ox, float oy, float oz) {
			return new AABB(minX + ox, minY + oy, minZ + oz, maxX + ox, maxY + oy, maxZ + oz);
		}

		private boolean overlap1D(float a0, float a1, float b0, float b1) {
			return a1 > b0 && a0 < b1;
		}

		private boolean overlapX(AABB o) {
			return overlap1D(minX, maxX, o.minX, o.maxX);
		}

		private boolean overlapY(AABB o) {
			return overlap1D(minY, maxY, o.minY, o.maxY);
		}

		private boolean overlapZ(AABB o) {
			return overlap1D(minZ, maxZ, o.minZ, o.maxZ);
		}

		public float collideX(AABB moving, float dx) {
			if (!overlapY(moving) || !overlapZ(moving))
				return dx;
			if (dx > 0f && moving.maxX <= minX)
				dx = Math.min(dx, minX - moving.maxX);
			else if (dx < 0f && moving.minX >= maxX)
				dx = Math.max(dx, maxX - moving.minX);
			return dx;
		}

		public float collideY(AABB moving, float dy) {
			if (!overlapX(moving) || !overlapZ(moving))
				return dy;
			if (dy > 0f && moving.maxY <= minY)
				dy = Math.min(dy, minY - moving.maxY);
			else if (dy < 0f && moving.minY >= maxY)
				dy = Math.max(dy, maxY - moving.minY);
			return dy;
		}

		public float collideZ(AABB moving, float dz) {
			if (!overlapX(moving) || !overlapY(moving))
				return dz;
			if (dz > 0f && moving.maxZ <= minZ)
				dz = Math.min(dz, minZ - moving.maxZ);
			else if (dz < 0f && moving.minZ >= maxZ)
				dz = Math.max(dz, maxZ - moving.minZ);
			return dz;
		}
	}
}