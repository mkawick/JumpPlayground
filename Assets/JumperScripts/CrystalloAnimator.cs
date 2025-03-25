using KinematicCharacterControllerNamespace;
using UnityEngine;

public class CrystalloAnimator : MonoBehaviour
{
    [SerializeField] TinyWizCharacterController characterController;
    [SerializeField] KinematicCharacterConfig motor;
    [SerializeField] Animator Animator;
    [SerializeField, Range(0.1f, 3)] float stopThreshold = 1;

    bool wasRunning = false;
    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        
    }

    // Update is called once per frame
    void Update()
    {
        if (motor.IsOnGround() == false)
        {
            Animator.SetBool("Jump", true);
        }
        else
        {
            Animator.SetBool("Jump", false);
        }

        float speed = motor.AnimatedMovementSpeed();

        if (speed > 3)
        {
            wasRunning = true;
        }
        if (speed < stopThreshold)// this needs to degrade over time
        {
            speed = 0;
            wasRunning = false;
        }
        if(characterController.CurrentCharacterState == CharacterState.Default)
        {
            Animator.SetFloat("ForwardMotion", speed);
        }
        else if (characterController.CurrentCharacterState == CharacterState.Dashing)
        {
            Animator.SetBool("Casting", true);
        }
    }
}
