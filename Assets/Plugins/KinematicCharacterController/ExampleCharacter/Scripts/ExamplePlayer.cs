using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using KinematicCharacterControllerNamespace;
using Terresquall;

namespace KinematicCharacterControllerNamespace
{
    public class ExamplePlayer : MonoBehaviour
    {
        public TinyWizCharacterController Character;
        public IsoCharacterCamera CharacterCamera;
        bool jumpPressed;
        bool dashPressed;

        private const string MouseXInput = "Mouse X";
        private const string MouseYInput = "Mouse Y";
        private const string MouseScrollInput = "Mouse ScrollWheel";
        private const string HorizontalInput = "Horizontal";
        private const string VerticalInput = "Vertical";

        private void Start()
        {
            jumpPressed = false;
            //Cursor.lockState = CursorLockMode.Locked;

            // Tell camera to follow transform
            CharacterCamera.SetFollowTransform(Character.CameraFollowPoint);

            // Ignore the character's collider(s) for camera obstruction checks
            CharacterCamera.IgnoredColliders.Clear();
            CharacterCamera.IgnoredColliders.AddRange(Character.GetComponentsInChildren<Collider>());
        }

        private void Update()
        {
            if (Input.GetMouseButtonDown(0))
            {
                //Cursor.lockState = CursorLockMode.Locked;
            }

            HandleCharacterInput();
        }

        private void LateUpdate()
        {
            // Handle rotating the camera along with physics movers
            if (CharacterCamera.RotateWithPhysicsMover && Character.Motor.AttachedRigidbody != null)
            {
                CharacterCamera.PlanarDirection = Character.Motor.AttachedRigidbody.GetComponent<PhysicsMover>().RotationDeltaFromInterpolation * CharacterCamera.PlanarDirection;
                CharacterCamera.PlanarDirection = Vector3.ProjectOnPlane(CharacterCamera.PlanarDirection, Character.Motor.CharacterUp).normalized;
            }

            HandleCameraInput();
        }

        private void HandleCameraInput()
        {
            // Create the look input vector for the camera
            float mouseLookAxisUp = Input.GetAxisRaw(MouseYInput);
            float mouseLookAxisRight = Input.GetAxisRaw(MouseXInput);
            Vector3 lookInputVector = new Vector3(mouseLookAxisRight, mouseLookAxisUp, 0f);

            // Prevent moving the camera while the cursor isn't locked
            if (Cursor.lockState != CursorLockMode.Locked)
            {
                lookInputVector = Vector3.zero;
            }

            // Input for zooming the camera (disabled in WebGL because it can cause problems)
            float scrollInput = -Input.GetAxis(MouseScrollInput);
#if UNITY_WEBGL
        scrollInput = 0f;
#endif

            // Apply inputs to the camera
            CharacterCamera.UpdateWithInput(Time.deltaTime, scrollInput, lookInputVector);

            // Handle toggling zoom level
            if (Input.GetMouseButtonDown(1))
            {
                CharacterCamera.TargetDistance = (CharacterCamera.TargetDistance == 0f) ? CharacterCamera.DefaultDistance : 0f;
            }
        }

        public static Vector2 Rotate(Vector2 v, float delta)
        {
            delta = Mathf.Deg2Rad * delta;
            return new Vector2(
                v.x * Mathf.Cos(delta) - v.y * Mathf.Sin(delta),
                v.x * Mathf.Sin(delta) + v.y * Mathf.Cos(delta)
            );
        }

        private void HandleCharacterInput()
        {
            PlayerCharacterInputs characterInputs = new PlayerCharacterInputs();

            // Build the CharacterInputs struct
            float y = Input.GetAxisRaw(VerticalInput) + VirtualJoystick.GetAxis("Vertical", 16);
            float x = Input.GetAxisRaw(HorizontalInput) + VirtualJoystick.GetAxis("Horizontal", 16);

            Vector2 newDir = Rotate(new Vector2(x, y), 45);

            characterInputs.MoveAxisForward = newDir.y;
            characterInputs.MoveAxisRight = newDir.x;
           
            //characterInputs.CameraRotation = CharacterCamera.Transform.rotation;
            characterInputs.JumpDown = Input.GetKeyDown(KeyCode.Space) | jumpPressed;
            characterInputs.CrouchDown = Input.GetKeyDown(KeyCode.C);
            characterInputs.CrouchUp = Input.GetKeyUp(KeyCode.C);

            if (dashPressed)
            {
                dashPressed = false; // clear
                characterInputs.dashStartFrame = Time.frameCount;
            }
            else
            {
                characterInputs.dashStartFrame = 0;
            }

            // Apply inputs to character
            Character.SetInputs(ref characterInputs);
            jumpPressed = false;
        }

        public void PlayerJumpButtonPressed()
        {
            jumpPressed = true;
        }
        public void DashButtonPressed()
        {
            dashPressed = true;
        }
    }
}