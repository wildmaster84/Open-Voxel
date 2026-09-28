package engine.rendering;

import org.lwjgl.opengl.GL30;

public class AnimatedTexture extends Texture {
	private final int frames;
	private final float frameHeightFraction;
	private final float frameDuration;
	private float timer = 0f;
	private int currentFrame = 0;

	public AnimatedTexture(String filepath, float frameDurationSeconds) {
		super(filepath);
		int detectedFrames = Math.max(1, height / Math.max(1, width));
		this.frames = detectedFrames;
		this.frameHeightFraction = 1.0f / (float) frames;
		this.frameDuration = Math.max(0.01f, frameDurationSeconds);
	}

	public void update(float deltaSeconds) {
		if (frames <= 1)
			return;
		timer += deltaSeconds;
		while (timer >= frameDuration) {
			timer -= frameDuration;
			currentFrame = (currentFrame + 1) % frames;
		}
	}

	public float getFrameOffset() {
		int totalHeight = this.height;
		int framePixels = this.width;
		if (totalHeight <= 0 || framePixels <= 0)
			return 0f;
		float frameHeightFraction = (float) framePixels / (float) totalHeight;
		float texel = 1.0f / (float) totalHeight;
		float pad = 0.5f * texel;
		return currentFrame * frameHeightFraction + pad;
	}

	public float getFrameScale() {
		int totalHeight = this.height;
		int framePixels = this.width;
		if (totalHeight <= 0 || framePixels <= 0)
			return 1f;
		float frameHeightFraction = (float) framePixels / (float) totalHeight;
		float texel = 1.0f / (float) totalHeight;
		float pad = 0.5f * texel;
		return frameHeightFraction - 2.0f * pad;
	}

	@Override
	public void bind() {
		GL30.glActiveTexture(GL30.GL_TEXTURE0);
		if (id != 0)
			GL30.glBindTexture(GL30.GL_TEXTURE_2D, id);
		// uFrameOffset/uFrameScale are set by ChunkMesh.bindForDraw with
		// cached uniform locations — glGet* queries here forced a driver
		// sync per batch per frame.
	}

	public float getFrameHeightFraction() {
		return frameHeightFraction;
	}
}
